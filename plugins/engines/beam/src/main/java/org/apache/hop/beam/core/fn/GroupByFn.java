package org.apache.hop.beam.core.fn;

import org.apache.beam.sdk.metrics.Counter;
import org.apache.beam.sdk.metrics.Metrics;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.values.KV;
import org.apache.hop.beam.core.BeamHop;
import org.apache.hop.beam.core.HopRow;
import org.apache.hop.beam.core.shared.AggregationType;
import org.apache.hop.beam.core.util.JsonRowMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

public class GroupByFn extends DoFn<KV<HopRow, Iterable<HopRow>>, HopRow> {


  private String counterName;
  private String groupRowMetaJson; // The data types of the group fields
  private String subjectRowMetaJson; // The data types of the subject fields
  private String[] aggregations; // The aggregation types
  private List<String> stepPluginClasses;
  private List<String> xpPluginClasses;

  private static final Logger LOG = LoggerFactory.getLogger( GroupByFn.class );

  private transient IRowMeta groupRowMeta;
  private transient IRowMeta subjectRowMeta;

  private transient AggregationType[] aggregationTypes = null;

  private transient Counter initCounter;
  private transient Counter readCounter;
  private transient Counter writtenCounter;
  private transient Counter errorCounter;

  public GroupByFn() {
  }

  public GroupByFn( String counterName, String groupRowMetaJson, List<String> stepPluginClasses, List<String> xpPluginClasses, String subjectRowMetaJson, String[] aggregations ) {
    this.counterName = counterName;
    this.groupRowMetaJson = groupRowMetaJson;
    this.stepPluginClasses = stepPluginClasses;
    this.xpPluginClasses = xpPluginClasses;
    this.subjectRowMetaJson = subjectRowMetaJson;
    this.aggregations = aggregations;
  }

  @Setup
  public void setUp() {
    try {
      readCounter = Metrics.counter( "read", counterName );
      writtenCounter = Metrics.counter( "written", counterName );
      errorCounter = Metrics.counter( "error", counterName );

      // Initialize Kettle Beam
      //
      BeamHop.init(stepPluginClasses, xpPluginClasses);
      groupRowMeta = JsonRowMeta.fromJson( groupRowMetaJson );
      subjectRowMeta = JsonRowMeta.fromJson( subjectRowMetaJson );
      aggregationTypes = new AggregationType[aggregations.length];
      for ( int i = 0; i < aggregationTypes.length; i++ ) {
        aggregationTypes[ i ] = AggregationType.getTypeFromName( aggregations[ i ] );
      }

      Metrics.counter( "init", counterName ).inc();
    } catch(Exception e) {
      errorCounter.inc();
      LOG.error("Error setup of grouping by ", e);
      throw new RuntimeException( "Unable setup of group by ", e );
    }
  }

  @ProcessElement
  public void processElement( ProcessContext processContext ) {

    try {

      // Get a KV
      //
      KV<HopRow, Iterable<HopRow>> inputElement = processContext.element();

      // Get the key row
      //
      HopRow groupHopRow = inputElement.getKey();
      Object[] groupRow = groupHopRow.getRow();

      // Initialize the aggregation results for this window
      //
      Object[] results = new Object[aggregationTypes.length];
      long[] counts = new long[aggregationTypes.length];
      for (int i=0;i<results.length;i++) {
        results[i] = null;
        counts[i]=0L;
      }

      Iterable<HopRow> subjectHopRows = inputElement.getValue();
      for ( HopRow subjectHopRow : subjectHopRows ) {
        Object[] subjectRow = subjectHopRow.getRow();
        readCounter.inc();

        // Aggregate this...
        //
        for (int i=0;i<aggregationTypes.length;i++) {
          IValueMeta subjectValueMeta = subjectRowMeta.getValueMeta( i );
          Object subject = subjectRow[i];
          Object result = results[i];

          switch(aggregationTypes[i]) {
            case AVERAGE:
              // Calculate count AND sum
              // Then correct below
              //
              if (!subjectValueMeta.isNull( subject )) {
                counts[ i ]++;
              }
            case SUM: {
              if ( result == null ) {
                result = subject;
              } else {
                switch ( subjectValueMeta.getType() ) {
                  case IValueMeta.TYPE_INTEGER:
                    result = (Long) result + (Long) subject;
                    break;
                  case IValueMeta.TYPE_NUMBER:
                    result = (Double)result + (Double)subject;
                    break;
                  default:
                    throw new HopException( "SUM aggregation not yet implemented for field and data type : "+subjectValueMeta.toString() );
                  }
                }
              }
              break;
            case COUNT_ALL:
              if (subject!=null) {
                if (result==null){
                  result = Long.valueOf( 1L );
                } else {
                  result = (Long)result + 1L;
                }
              }
              break;
            case MIN:
              if (subjectValueMeta.isNull(result)) {
                // Previous result was null?  Then take the subject
                result = subject;
              } else {
                if (subjectValueMeta.compare( subject, result )<0) {
                  result = subject;
                }
              }
              break;
            case MAX:
              if (subjectValueMeta.isNull(result)) {
                // Previous result was null?  Then take the subject
                result = subject;
              } else {
                if (subjectValueMeta.compare( subject, result )>0) {
                  result = subject;
                }
              }
              break;
            case FIRST_INCL_NULL:
              if (counts[i]==0) {
                counts[i]++;
                result = subject;
              }
              break;
            case LAST_INCL_NULL:
              result = subject;
              break;
            case FIRST:
              if (!subjectValueMeta.isNull(subject) && counts[i]==0) {
                counts[i]++;
                result = subject;
              }
              break;
            case LAST:
              if (!subjectValueMeta.isNull(subject)) {
                result = subject;
              }
              break;
            default:
              throw new HopException( "Sorry, aggregation type yet: "+aggregationTypes[i].name() +" isn't implemented yet" );

          }
          results[i] = result;
        }
      }

      // Do a pass to correct average
      //
      for (int i=0;i<results.length;i++) {
        IValueMeta subjectValueMeta = subjectRowMeta.getValueMeta( i );
        switch(aggregationTypes[i]) {
          case AVERAGE:
            switch(subjectValueMeta.getType()) {
              case IValueMeta.TYPE_NUMBER:
                double dbl = (Double)results[i];
                if (counts[i]!=0) {
                  dbl/=counts[i];
                }
                results[i] = dbl;
                break;
              case IValueMeta.TYPE_INTEGER:
                long lng = (Long)results[i];
                if (counts[i]!=0) {
                  lng/=counts[i];
                }
                results[i] = lng;
                break;
              case IValueMeta.TYPE_BIGNUMBER:
                BigDecimal bd = (BigDecimal) results[i];
                if (counts[i]!=0) {
                  bd = bd.divide( BigDecimal.valueOf( counts[i] ) );
                }
                results[i] = bd;
              default:
                throw new HopException( "Unable to calculate average on data type : "+subjectValueMeta.getTypeDesc() );
            }
        }
      }

      // Now we have the results
      // Concatenate both group and result...
      //
      Object[] resultRow = RowDataUtil.allocateRowData( groupRowMeta.size()+subjectRowMeta.size() );
      int index = 0;
      for (int i=0;i<groupRowMeta.size();i++) {
        resultRow[index++] = groupRow[i];
      }
      for (int i=0;i<subjectRowMeta.size();i++) {
        resultRow[index++] = results[i];
      }

      // Send it on its way
      //
      processContext.output( new HopRow( resultRow ) );
      writtenCounter.inc();

    } catch(Exception e) {
      errorCounter.inc();
      LOG.error("Error grouping by ", e);
      throw new RuntimeException( "Unable to split row into group and subject ", e );
    }
  }


  /**
   * Gets aggregations
   *
   * @return value of aggregations
   */
  public String[] getAggregations() {
    return aggregations;
  }

  /**
   * @param aggregations The aggregations to set
   */
  public void setAggregations( String[] aggregations ) {
    this.aggregations = aggregations;
  }
}
