package org.apache.hop.beam.core.fn;

import org.apache.beam.sdk.metrics.Counter;
import org.apache.beam.sdk.metrics.Metrics;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.values.KV;
import org.apache.hop.beam.core.BeamHop;
import org.apache.hop.beam.core.HopRow;
import org.apache.hop.beam.core.util.JsonRowMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.IRowMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class KVLongStringToHopRowFn extends DoFn<KV<Long,String>, HopRow> {

  private String rowMetaJson;
  private String transformName;
  private List<String> stepPluginClasses;
  private List<String> xpPluginClasses;

  private static final Logger LOG = LoggerFactory.getLogger( KVLongStringToHopRowFn.class );
  private final Counter numErrors = Metrics.counter( "main", "BeamSubscribeTransformErrors" );

  private IRowMeta rowMeta;
  private transient Counter initCounter;
  private transient Counter inputCounter;
  private transient Counter writtenCounter;

  public KVLongStringToHopRowFn( String transformName, String rowMetaJson, List<String> stepPluginClasses, List<String> xpPluginClasses ) {
    this.transformName = transformName;
    this.rowMetaJson = rowMetaJson;
    this.stepPluginClasses = stepPluginClasses;
    this.xpPluginClasses = xpPluginClasses;
  }

  @Setup
  public void setUp() {
    try {
      inputCounter = Metrics.counter( "input", transformName );
      writtenCounter = Metrics.counter( "written", transformName );

      // Initialize Kettle Beam
      //
      BeamHop.init( stepPluginClasses, xpPluginClasses );
      rowMeta = JsonRowMeta.fromJson( rowMetaJson );

      Metrics.counter( "init", transformName ).inc();
    } catch ( Exception e ) {
      numErrors.inc();
      LOG.error( "Error in setup of KV<Long,String> to Kettle Row conversion function", e );
      throw new RuntimeException( "Error in setup of KV<Long,String> to Kettle Row conversion function", e );
    }
  }

  @ProcessElement
  public void processElement( ProcessContext processContext ) {
    try {

      KV<Long,String> kv = processContext.element();
      inputCounter.inc();

      Object[] outputRow = RowDataUtil.allocateRowData( rowMeta.size() );
      outputRow[ 0 ] = kv.getKey();
      outputRow[ 1 ] = kv.getValue();

      processContext.output( new HopRow( outputRow ) );
      writtenCounter.inc();

    } catch ( Exception e ) {
      numErrors.inc();
      LOG.error( "Error in KV<Long,String> to Kettle Row conversion function", e );
      throw new RuntimeException( "Error in KV<Long,String> to Kettle Row conversion function", e );
    }
  }
}