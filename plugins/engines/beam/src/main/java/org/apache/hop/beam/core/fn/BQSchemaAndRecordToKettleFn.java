package org.apache.hop.beam.core.fn;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableSchema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
import org.apache.beam.sdk.io.gcp.bigquery.SchemaAndRecord;
import org.apache.beam.sdk.metrics.Counter;
import org.apache.beam.sdk.metrics.Metrics;
import org.apache.beam.sdk.transforms.SerializableFunction;
import org.apache.hop.beam.core.BeamHop;
import org.apache.hop.beam.core.HopRow;
import org.apache.hop.beam.core.util.JsonRowMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * BigQuery Avro SchemaRecord to HopRow
 */
public class BQSchemaAndRecordToKettleFn implements SerializableFunction<SchemaAndRecord, HopRow> {

  private String transformName;
  private String rowMetaJson;
  private List<String> stepPluginClasses;
  private List<String> xpPluginClasses;

  private transient Counter initCounter;
  private transient Counter inputCounter;
  private transient Counter writtenCounter;
  private transient Counter errorCounter;

  // Log and count parse errors.
  private static final Logger LOG = LoggerFactory.getLogger( BQSchemaAndRecordToKettleFn.class );

  private transient IRowMeta rowMeta;
  private transient SimpleDateFormat simpleDateTimeFormat;
  private transient SimpleDateFormat simpleDateFormat;

  public BQSchemaAndRecordToKettleFn( String transformName, String rowMetaJson, List<String> stepPluginClasses, List<String> xpPluginClasses ) {
    this.transformName = transformName;
    this.rowMetaJson = rowMetaJson;
    this.stepPluginClasses = stepPluginClasses;
    this.xpPluginClasses = xpPluginClasses;
  }

  public HopRow apply( SchemaAndRecord schemaAndRecord ) {

    try {

      GenericRecord record = schemaAndRecord.getRecord();
      TableSchema tableSchema = schemaAndRecord.getTableSchema();

      if ( rowMeta == null ) {

        inputCounter = Metrics.counter( "input", transformName );
        writtenCounter = Metrics.counter( "written", transformName );
        errorCounter = Metrics.counter( "error", transformName );

        // Initialize Kettle
        //
        BeamHop.init( stepPluginClasses, xpPluginClasses );
        rowMeta = JsonRowMeta.fromJson( rowMetaJson );

        int[] valueTypes = new int[rowMeta.size()];

        List<TableFieldSchema> fields = tableSchema.getFields();
        for (int i=0;i<fields.size();i++) {
          TableFieldSchema fieldSchema = fields.get( i );
          String name = fieldSchema.getName();
          int index = rowMeta.indexOfValue( name );
          // Ignore everything we didn't ask for.
          //
          if (index>=0) {
            String avroTypeString = fieldSchema.getType();
            try {
              AvroType avroType = AvroType.valueOf( avroTypeString );
              valueTypes[index] = avroType.getKettleType();
            } catch(IllegalArgumentException e) {
              throw new RuntimeException( "Unable to recognize data type '"+avroTypeString+"'", e );
            }
          }
        }

        // See that we got all the fields covered...
        //
        for (int i = 0;i<rowMeta.size();i++) {
          if (valueTypes[i]==0) {
            IValueMeta valueMeta = rowMeta.getValueMeta( i );
            throw new RuntimeException( "Unable to find field '"+valueMeta.getName()+"'" );
          }
        }

        simpleDateTimeFormat = new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ss" );
        simpleDateTimeFormat.setLenient( true );
        simpleDateFormat = new SimpleDateFormat( "yyyy-MM-dd" );
        simpleDateFormat.setLenient( true );
        Metrics.counter( "init", transformName ).inc();
      }

      inputCounter.inc();

      // Convert to the requested Kettle Data types
      //
      Object[] row = RowDataUtil.allocateRowData( rowMeta.size() );
      for (int index=0; index < rowMeta.size() ; index++) {
        IValueMeta valueMeta = rowMeta.getValueMeta( index );
        Object srcData = record.get(valueMeta.getName());
        if (srcData!=null) {
          switch(valueMeta.getType()) {
            case IValueMeta.TYPE_STRING:
              row[index] = srcData.toString();
              break;
            case IValueMeta.TYPE_INTEGER:
              row[index] = (Long)srcData;
              break;
            case IValueMeta.TYPE_NUMBER:
              row[index] = (Double)srcData;
              break;
            case IValueMeta.TYPE_BOOLEAN:
              row[index] = (Boolean)srcData;
              break;
            case IValueMeta.TYPE_DATE:
              // We get a Long back
              //
              String datetimeString = ((Utf8) srcData).toString();
              if (datetimeString.length()==10) {
                row[index] = simpleDateFormat.parse( datetimeString );
              } else {
                row[ index ] = simpleDateTimeFormat.parse( datetimeString );
              }
              break;
            default:
              throw new RuntimeException("Conversion from Avro JSON to Kettle is not yet supported for Kettle data type '"+valueMeta.getTypeDesc()+"'");
          }
        }
      }

      // Pass the row to the process context
      //
      writtenCounter.inc();
      return new HopRow( row );

    } catch ( Exception e ) {
      errorCounter.inc();
      LOG.error( "Error converting BQ Avro data into Kettle rows : " + e.getMessage() );
      throw new RuntimeException( "Error converting BQ Avro data into Kettle rows", e );

    }
  }

  //  From:
  //         https://cloud.google.com/dataprep/docs/html/BigQuery-Data-Type-Conversions_102563896
  //
  public enum AvroType {
    STRING(IValueMeta.TYPE_STRING),
    BYTES(IValueMeta.TYPE_STRING),
    INTEGER(IValueMeta.TYPE_INTEGER),
    INT64(IValueMeta.TYPE_INTEGER),
    FLOAT(IValueMeta.TYPE_NUMBER),
    FLOAT64(IValueMeta.TYPE_NUMBER),
    BOOLEAN(IValueMeta.TYPE_BOOLEAN),
    BOOL(IValueMeta.TYPE_BOOLEAN),
    TIMESTAMP(IValueMeta.TYPE_DATE),
    DATE(IValueMeta.TYPE_DATE),
    TIME(IValueMeta.TYPE_DATE),
    DATETIME(IValueMeta.TYPE_DATE),
    ;

    private int kettleType;

    private AvroType(int kettleType) {
      this.kettleType = kettleType;
    }

    /**
     * Gets kettleType
     *
     * @return value of kettleType
     */
    public int getKettleType() {
      return kettleType;
    }
  }

}
