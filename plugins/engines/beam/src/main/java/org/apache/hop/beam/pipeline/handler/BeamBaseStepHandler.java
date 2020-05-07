package org.apache.hop.beam.pipeline.handler;

import org.apache.hop.beam.metastore.BeamJobConfig;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.metastore.api.IMetaStore;

import java.util.List;

public class BeamBaseStepHandler {

  protected IMetaStore metaStore;
  protected PipelineMeta pipelineMeta;
  protected List<String> stepPluginClasses;
  protected List<String> xpPluginClasses;
  protected boolean input;
  protected boolean output;
  protected BeamJobConfig beamJobConfig;

  public BeamBaseStepHandler( BeamJobConfig beamJobConfig, boolean input, boolean output, IMetaStore metaStore, PipelineMeta pipelineMeta, List<String> stepPluginClasses, List<String> xpPluginClasses ) {
    this.beamJobConfig = beamJobConfig;
    this.input = input;
    this.output = output;
    this.metaStore = metaStore;
    this.pipelineMeta = pipelineMeta;
    this.stepPluginClasses = stepPluginClasses;
    this.xpPluginClasses = xpPluginClasses;
  }

  /**
   * Gets metaStore
   *
   * @return value of metaStore
   */
  public IMetaStore getMetaStore() {
    return metaStore;
  }

  /**
   * @param metaStore The metaStore to set
   */
  public void setMetaStore( IMetaStore metaStore ) {
    this.metaStore = metaStore;
  }

  /**
   * Gets pipelineMeta
   *
   * @return value of pipelineMeta
   */
  public PipelineMeta getPipelineMeta() {
    return pipelineMeta;
  }

  /**
   * @param pipelineMeta The pipelineMeta to set
   */
  public void setPipelineMeta( PipelineMeta pipelineMeta ) {
    this.pipelineMeta = pipelineMeta;
  }

  /**
   * Gets stepPluginClasses
   *
   * @return value of stepPluginClasses
   */
  public List<String> getStepPluginClasses() {
    return stepPluginClasses;
  }

  /**
   * @param stepPluginClasses The stepPluginClasses to set
   */
  public void setStepPluginClasses( List<String> stepPluginClasses ) {
    this.stepPluginClasses = stepPluginClasses;
  }

  /**
   * Gets xpPluginClasses
   *
   * @return value of xpPluginClasses
   */
  public List<String> getXpPluginClasses() {
    return xpPluginClasses;
  }

  /**
   * @param xpPluginClasses The xpPluginClasses to set
   */
  public void setXpPluginClasses( List<String> xpPluginClasses ) {
    this.xpPluginClasses = xpPluginClasses;
  }

  /**
   * Gets input
   *
   * @return value of input
   */
  public boolean isInput() {
    return input;
  }

  /**
   * @param input The input to set
   */
  public void setInput( boolean input ) {
    this.input = input;
  }

  /**
   * Gets output
   *
   * @return value of output
   */
  public boolean isOutput() {
    return output;
  }

  /**
   * @param output The output to set
   */
  public void setOutput( boolean output ) {
    this.output = output;
  }
}
