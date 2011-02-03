package com.hanhuy.android.c2dm.generic;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.IdFunctionCall;
import org.mozilla.javascript.IdFunctionObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;

import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class JavaAdapter implements IdFunctionCall {
    public static void init(Context c, Scriptable scope, boolean sealed) {
        JavaAdapter obj = new JavaAdapter();
        IdFunctionObject ctor = new IdFunctionObject(
                obj, FTAG, Id_JavaAdapter, "JavaAdapter", 1, scope);
        ctor.markAsConstructor(null);
        if (sealed)
            ctor.sealObject();

        ctor.exportAsScopeProperty();
    }

    @Override
    public Object execIdCall(IdFunctionObject f, Context c, Scriptable scope,
                             Scriptable thisObj, Object[] args)
    {
        if (f.hasTag(FTAG)) {
            if (f.methodId() == Id_JavaAdapter)
                return wrap(c, scope, args);
        }
        throw f.unknown();
    }

    private Scriptable wrap(Context c, Scriptable scope, Object[] args) {
        // TODO handle more than 2 arguments (more than 1 interface)
        if (args.length == 2) {
            Class<?> clazz = null;
            Object obj = args[0];
            if (obj instanceof Wrapper) {
                Object o = ((Wrapper)obj).unwrap();
                if (o instanceof Class && ((Class)o).isInterface())
                    clazz = (Class) o;
            } else if (obj instanceof Class) {
                if (((Class)obj).isInterface())
                    clazz = (Class) obj;
            }
            if (clazz == null) {
                throw Context.reportRuntimeError(
                        "JavaAdapter: no Class arg specified");
            }

            Scriptable top = ScriptableObject.getTopLevelScope(scope);
            return c.toObject(Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[] { clazz } , new Invoker(args[1])), top);
        } else {
            throw Context.reportRuntimeError("JavaAdapter arguments: " +
                    args.length + ", expected: 2");
        }
    }

    private static class Invoker implements InvocationHandler {
        private Scriptable self;
        private ContextFactory factory;
        Invoker(Object thiz) {
            self = (Scriptable) thiz;
        }
        public Object invoke(Object proxy, Method m, final Object[] args)
        throws Throwable {
            Object o = ScriptableObject.getProperty(self, m.getName());
            if (o == Scriptable.NOT_FOUND)
                return Undefined.instance;
            if (!(o instanceof Function))
                throw ScriptRuntime.notFunctionError(o, m.getName());
            final Function f = (Function) o;
            final Scriptable scope = f.getParentScope();
            final Context c = Context.getCurrentContext();
            if (c == null) {
                if (factory == null)
                    factory = ContextFactory.getGlobal();
                return factory.call(new ContextAction() {
                    public Object run(Context c) {
                        return doInvoke(c, scope, f, args);
                    }
                });
            } else {
                return doInvoke(c, scope, f, args);
            }
        }
        // first naive implementation
        private Object doInvoke(Context c, Scriptable scope, Function f,
                Object[] args) {
            for (int i = 0, j = args.length; i < j; i++) {
                if (!(args[i] instanceof Scriptable))
                    args[i] = c.getWrapFactory().wrap(c, scope, args[i], null);
            }
            return f.call(c, scope, self, args);
        }
    }

    private static final int Id_JavaAdapter = 1;
    private static final Object FTAG = "JavaAdapter";
}
