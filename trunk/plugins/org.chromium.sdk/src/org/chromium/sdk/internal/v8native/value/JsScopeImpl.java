// Copyright (c) 2009 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.sdk.internal.v8native.value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.chromium.sdk.JsObject;
import org.chromium.sdk.JsScope;
import org.chromium.sdk.JsValue;
import org.chromium.sdk.JsVariable;
import org.chromium.sdk.internal.v8native.CallFrameImpl;
import org.chromium.sdk.internal.v8native.protocol.V8ProtocolUtil;
import org.chromium.sdk.internal.v8native.protocol.input.ScopeRef;
import org.chromium.sdk.internal.v8native.protocol.input.data.ObjectValueHandle;
import org.chromium.sdk.util.AsyncFuture;
import org.chromium.sdk.util.AsyncFuture.SyncOperation;
import org.chromium.sdk.util.MethodIsBlockingException;

/**
 * A generic implementation of the JsScope interface.
 */
public abstract class JsScopeImpl<D> implements JsScope {

  private final CallFrameImpl callFrameImpl;
  private final int scopeIndex;
  private final Type type;
  private final AtomicReference<AsyncFuture<D>> deferredDataRef =
      new AtomicReference<AsyncFuture<D>>(null);

  public static JsScopeImpl<?> create(CallFrameImpl callFrameImpl, ScopeRef scopeRef) {
    Type type = convertType((int) scopeRef.type());
    if (type == Type.WITH) {
      return new With(callFrameImpl, type, (int) scopeRef.index());
    } else {
      return new NoWith(callFrameImpl, type, (int) scopeRef.index());
    }
  }

  protected JsScopeImpl(CallFrameImpl callFrameImpl, Type type, int scopeIndex) {
    this.callFrameImpl = callFrameImpl;
    this.type = type;
    this.scopeIndex = scopeIndex;
  }

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public List<? extends JsVariable> getVariables() throws MethodIsBlockingException {
    return getVariables(getDeferredData());
  }

  protected D getDeferredData() throws MethodIsBlockingException {
    AsyncFuture<D> future = deferredDataRef.get();
    ValueLoaderImpl valueLoader = callFrameImpl.getInternalContext().getValueLoader();
    int cacheState = valueLoader.getCurrentCacheState();
    boolean restartOperation;
    if (future == null) {
      // Do not restart operation if other thread has already started it.
      restartOperation = false;
    } else {
      D result = future.getSync();
      int dataCacheState = getDataCacheState(result);
      if (dataCacheState == cacheState) {
        return result;
      }
      restartOperation = true;
    }
    SyncOperation<D> loadOperation = createLoadDataOperation(valueLoader, cacheState);
    // Create future, so that other threads didn't start operations of their own.
    AsyncFuture.initializeReference(deferredDataRef, loadOperation.asAsyncOperation(),
        restartOperation);
    loadOperation.execute();
    return deferredDataRef.get().getSync();
  }

  protected abstract SyncOperation<D> createLoadDataOperation(ValueLoaderImpl valueLoader,
      int cacheState);

  protected abstract List<? extends JsVariable> getVariables(D data)
      throws MethodIsBlockingException;

  protected abstract int getDataCacheState(D data);

  protected CallFrameImpl getCallFrameImpl() {
    return callFrameImpl;
  }

  protected ObjectValueHandle loadScopeObject(ValueLoaderImpl valueLoader)
      throws MethodIsBlockingException {
    return valueLoader.loadScopeFields(scopeIndex, callFrameImpl.getIdentifier());
  }

  public static Type convertType(int typeCode) {
    Type type = CODE_TO_TYPE.get(typeCode);
    if (type == null) {
      type = Type.UNKNOWN;
    }
    return type;
  }

  private static class NoWith extends JsScopeImpl<NoWith.DeferredData> {
    NoWith(CallFrameImpl callFrameImpl, Type type, int scopeIndex) {
      super(callFrameImpl, type, scopeIndex);
    }

    @Override
    public WithScope asWithScope() {
      return null;
    }

    @Override
    protected List<? extends JsVariable> getVariables(DeferredData data) {
      return data.variables;
    }

    @Override
    protected int getDataCacheState(DeferredData data) {
      return data.cacheState;
    }

    @Override
    protected SyncOperation<DeferredData> createLoadDataOperation(
        final ValueLoaderImpl valueLoader, final int cacheState) {
      return new SyncOperation<DeferredData>() {
        @Override
        protected DeferredData runSync() throws MethodIsBlockingException {
          List<JsVariable> list = load(valueLoader);
          return new DeferredData(list, cacheState);
        }
      };
    }

    private List<JsVariable> load(ValueLoaderImpl valueLoader)
        throws MethodIsBlockingException {
      ObjectValueHandle scopeObject = loadScopeObject(valueLoader);
      if (scopeObject == null) {
        return Collections.emptyList();
      }
      List<? extends PropertyReference> propertyRefs =
          V8ProtocolUtil.extractObjectProperties(scopeObject);

      List<ValueMirror> propertyMirrors = valueLoader.getOrLoadValueFromRefs(propertyRefs);

      List<JsVariable> properties = new ArrayList<JsVariable>(propertyMirrors.size());
      for (int i = 0; i < propertyMirrors.size(); i++) {
        // This name should be string. We are making it string as a fall-back strategy.
        String varNameStr = propertyRefs.get(i).getName().toString();
        properties.add(new JsVariableImpl(valueLoader, propertyMirrors.get(i), varNameStr));
      }
      return properties;
    }

    static class DeferredData {
      final List<? extends JsVariable> variables;
      final int cacheState;

      DeferredData(List<? extends JsVariable> variables, int cacheState) {
        this.variables = variables;
        this.cacheState = cacheState;
      }
    }
  }

  private static class With extends JsScopeImpl<With.DeferredData> implements JsScope.WithScope {
    With(CallFrameImpl callFrameImpl, Type type, int scopeIndex) {
      super(callFrameImpl, type, scopeIndex);
    }

    @Override
    public WithScope asWithScope() {
      return this;
    }

    @Override
    public JsValue getWithArgument() throws MethodIsBlockingException {
      return getDeferredData().jsValue;
    }

    @Override
    protected SyncOperation<DeferredData> createLoadDataOperation(
        final ValueLoaderImpl valueLoader, final int cacheState) {
      return new SyncOperation<DeferredData>() {
        @Override
        protected DeferredData runSync() throws MethodIsBlockingException {
          return load(valueLoader, cacheState);
        }
      };
    }

    private DeferredData load(ValueLoaderImpl valueLoader, int cacheState)
        throws MethodIsBlockingException {
      ObjectValueHandle scopeObject = loadScopeObject(valueLoader);
      ValueMirror mirror = valueLoader.addDataToMap(scopeObject.getSuper());
      JsValue jsValue = JsVariableImpl.createValue(valueLoader, mirror, "<with object>");
      return new DeferredData(jsValue, cacheState);
    }

    @Override
    protected List<? extends JsVariable> getVariables(DeferredData data)
        throws MethodIsBlockingException {
      if (data.orderedProperties == null) {
        List<? extends JsVariable> list;
        JsObject jsObject = data.jsValue.asObject();
        if (jsObject == null) {
          list = Collections.emptyList();
        } else {
          list = new ArrayList<JsVariable>(jsObject.getProperties());
        }
        data.orderedProperties = list;
      }
      return data.orderedProperties;
    }

    @Override
    protected int getDataCacheState(DeferredData data) {
      return data.cacheState;
    }

    static class DeferredData {
      final JsValue jsValue;
      final int cacheState;

      // Stores properties in the list -- the order is unspecified, but fixed.
      volatile List<? extends JsVariable> orderedProperties = null;

      DeferredData(JsValue jsValue, int cacheState) {
        this.jsValue = jsValue;
        this.cacheState = cacheState;
      }
    }
  }

  private static final Map<Integer, Type> CODE_TO_TYPE;
  static {
    CODE_TO_TYPE = new HashMap<Integer, Type>();
    CODE_TO_TYPE.put(0, Type.GLOBAL);
    CODE_TO_TYPE.put(1, Type.LOCAL);
    CODE_TO_TYPE.put(2, Type.WITH);
    CODE_TO_TYPE.put(3, Type.CLOSURE);
    CODE_TO_TYPE.put(4, Type.CATCH);
  }
}