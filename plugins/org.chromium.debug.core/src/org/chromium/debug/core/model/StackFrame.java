// Copyright (c) 2009 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.debug.core.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.chromium.debug.core.ChromiumDebugPlugin;
import org.chromium.sdk.CallFrame;
import org.chromium.sdk.JsArray;
import org.chromium.sdk.JsObject;
import org.chromium.sdk.JsScope;
import org.chromium.sdk.JsValue;
import org.chromium.sdk.JsVariable;
import org.chromium.sdk.Script;
import org.chromium.sdk.internal.tools.v8.MethodIsBlockingException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IRegisterGroup;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.osgi.util.NLS;

/**
 * An IStackFrame implementation over a JsStackFrame instance.
 */
public class StackFrame extends DebugElementImpl implements IStackFrame {

  private final JavascriptThread thread;

  private final CallFrame stackFrame;

  private IVariable[] variables;

  /**
   * Constructs a stack frame for the given handler using the FrameMirror data
   * from the remote V8 VM.
   *
   * @param debugTarget the global parent
   * @param thread for which the stack frame is created
   * @param stackFrame an underlying SDK stack frame
   */
  public StackFrame(DebugTargetImpl debugTarget, JavascriptThread thread, CallFrame stackFrame) {
    super(debugTarget);
    this.thread = thread;
    this.stackFrame = stackFrame;
  }

  public CallFrame getCallFrame() {
    return stackFrame;
  }

  public IThread getThread() {
    return thread;
  }

  public IVariable[] getVariables() throws DebugException {
    if (variables == null) {
      try {
        variables = wrapScopes(getDebugTarget(), stackFrame.getVariableScopes());
      } catch (RuntimeException e) {
        // We shouldn't throw RuntimeException from here, because calling
        // ElementContentProvider#update will forget to call update.done().
        throw new DebugException(new Status(IStatus.ERROR, ChromiumDebugPlugin.PLUGIN_ID,
            "Failed to read variables", e)); //$NON-NLS-1$
      }
    }
    return variables;
  }

  static IVariable[] wrapVariables(
      DebugTargetImpl debugTarget, Collection<? extends JsVariable> jsVars) {
    List<Variable> vars = new ArrayList<Variable>(jsVars.size());
    for (JsVariable jsVar : jsVars) {
      vars.add(new Variable(debugTarget, jsVar));
    }
    return vars.toArray(new IVariable[vars.size()]);
  }

  static IVariable[] wrapScopes(DebugTargetImpl debugTarget, List<? extends JsScope> jsScopes) {
    List<Variable> vars = new ArrayList<Variable>();

    for (JsScope scope : jsScopes) {
      if (scope.getType() == JsScope.Type.GLOBAL) {
        vars.add(new Variable(debugTarget, wrapScopeAsVariable(scope)));
      } else {
        for (JsVariable var : scope.getVariables()) {
          vars.add(new Variable(debugTarget, var));
        }
      }
    }

    IVariable[] result = new IVariable[vars.size()];
    // Return in reverse order.
    for (int i = 0; i < result.length; i++) {
      result[result.length - i - 1] = vars.get(i);
    }
    return result;
  }

  private static JsVariable wrapScopeAsVariable(final JsScope jsScope) {
    class ScopeObjectVariable implements JsVariable, JsObject {
      public String getFullyQualifiedName() {
        return getName();
      }
      public String getName() {
        // TODO(peter.rybin): should we localize it?
        return "<" + jsScope.getType() + ">";
      }
      public JsValue getValue() throws UnsupportedOperationException {
        return this;
      }
      public boolean isMutable() {
        return false;
      }
      public boolean isReadable() {
        return true;
      }
      public void setValue(String newValue, SetValueCallback callback)
          throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
      }
      public JsArray asArray() {
        return null;
      }
      public String getClassName() {
        // TODO(peter.rybin): should we localize it?
        return "#Scope";
      }
      public Collection<? extends JsVariable> getProperties() throws MethodIsBlockingException {
        return jsScope.getVariables();
      }
      public JsVariable getProperty(String name) {
        for (JsVariable var : getProperties()) {
          if (var.getName().equals(name)) {
            return var;
          }
        }
        return null;
      }
      public JsObject asObject() {
        return this;
      }
      public Type getType() {
        return Type.TYPE_OBJECT;
      }
      public String getValueString() {
        return getClassName();
      }
    }
    return new ScopeObjectVariable();
  }

  public boolean hasVariables() throws DebugException {
    return stackFrame.getVariableScopes().size() > 0;
  }

  public int getLineNumber() throws DebugException {
    // convert 0-based to 1-based
    return stackFrame.getLineNumber() + 1;
  }

  public int getCharStart() throws DebugException {
    return stackFrame.getCharStart();
  }

  public int getCharEnd() throws DebugException {
    return -1;
  }

  public String getName() throws DebugException {
    String name = stackFrame.getFunctionName();
    Script script = stackFrame.getScript();
    if (script == null) {
      return Messages.StackFrame_UnknownScriptName;
    }
    int line = script.getStartLine() + getLineNumber();
    if (line != -1) {
      name = NLS.bind(Messages.StackFrame_NameFormat, new Object[] {name, script.getName(), line});
    }
    return name;
  }

  public IRegisterGroup[] getRegisterGroups() throws DebugException {
    return null;
  }

  public boolean hasRegisterGroups() throws DebugException {
    return false;
  }

  public boolean canStepInto() {
    return getThread().canStepInto();
  }

  public boolean canStepOver() {
    return getThread().canStepOver();
  }

  public boolean canStepReturn() {
    return getThread().canStepReturn();
  }

  public boolean isStepping() {
    return getThread().isStepping();
  }

  public void stepInto() throws DebugException {
    getThread().stepInto();
  }

  public void stepOver() throws DebugException {
    getThread().stepOver();
  }

  public void stepReturn() throws DebugException {
    getThread().stepReturn();
  }

  public boolean canResume() {
    return getThread().canResume();
  }

  public boolean canSuspend() {
    return getThread().canSuspend();
  }

  public boolean isSuspended() {
    return getThread().isSuspended();
  }

  public void resume() throws DebugException {
    getThread().resume();
  }

  public void suspend() throws DebugException {
    getThread().suspend();
  }

  public boolean canTerminate() {
    return getThread().canTerminate();
  }

  public boolean isTerminated() {
    return getThread().isTerminated();
  }

  public void terminate() throws DebugException {
    getThread().terminate();
  }

  /**
   * Returns the name of the source file this stack frame is associated with.
   *
   * @return the name of the source file this stack frame is associated with
   */
  String getSourceName() {
    Script script = stackFrame.getScript();
    if (script == null) {
      return Messages.StackFrame_UnknownScriptName;
    }
    return script.getName();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof StackFrame) {
      StackFrame other = (StackFrame) obj;
      return other.stackFrame.equals(this.stackFrame);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return stackFrame.hashCode();
  }

}