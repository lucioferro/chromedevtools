// Generated source.
// Generator: org.chromium.sdk.internal.wip.tools.protocolgenerator.Generator
// Origin: http://svn.webkit.org/repository/webkit/!svn/bc/87771/trunk/Source/WebCore/inspector/Inspector.json@86959

package org.chromium.sdk.internal.wip.protocol.output.debugger;

/**
Edits JavaScript script live.
 */
public class EditScriptSourceParams extends org.chromium.sdk.internal.wip.protocol.output.WipParamsWithResponse<org.chromium.sdk.internal.wip.protocol.input.debugger.EditScriptSourceData> {
  /**
   @param sourceId Id of the script to edit.
   @param scriptSource New content of the script.
   */
  public EditScriptSourceParams(String sourceId, String scriptSource) {
    this.put("sourceId", sourceId);
    this.put("scriptSource", scriptSource);
  }

  public static final String METHOD_NAME = org.chromium.sdk.internal.wip.protocol.BasicConstants.Domain.DEBUGGER + ".editScriptSource";

  @Override protected String getRequestName() {
    return METHOD_NAME;
  }

  @Override public org.chromium.sdk.internal.wip.protocol.input.debugger.EditScriptSourceData parseResponse(org.chromium.sdk.internal.wip.protocol.input.WipCommandResponse.Data data, org.chromium.sdk.internal.wip.protocol.input.WipGeneratedParserRoot parser) throws org.chromium.sdk.internal.protocolparser.JsonProtocolParseException {
    return parser.parseDebuggerEditScriptSourceData(data.getUnderlyingObject());
  }

}