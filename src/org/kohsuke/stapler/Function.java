package org.kohsuke.stapler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.ServletException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.ArrayList;

/**
 * Abstracts the difference between normal instance methods and
 * static duck-typed methods.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class Function {
    /**
     * Gets the method name.
     */
    abstract String getName();

    /**
     * Gets the type of parameters in a single array.
     */
    abstract Class[] getParameterTypes();

    /**
     * Gets the annotations on parameters.
     */
    abstract Annotation[][] getParameterAnnotatoins();

    /**
     * Use the given arguments as the first N arguments,
     * then figure out the rest of the arguments by looking at parameter annotations,
     * then finally call {@link #invoke}.
     */
    Object bindAndinvoke(HttpServletRequest req, Object o, Object... args) throws IllegalAccessException, InvocationTargetException, ServletException {
        Class[] types = getParameterTypes();
        Annotation[][] annotations = getParameterAnnotatoins();

        // initial arguments
        List<Object> arguments = new ArrayList<Object>(types.length);
        for (Object a : args)
            arguments.add(a);

        // figure out the rest
        for( int i=arguments.size(); i<types.length; i++ )
            arguments.add(AnnotationHandler.handle(req,annotations[i],types[i]));

        return invoke(req,o,arguments.toArray());
    }

    /**
     * Invokes the method.
     */
    abstract Object invoke(HttpServletRequest req, Object o, Object... args) throws IllegalAccessException, InvocationTargetException;

    final Function protectBy(Method m) {
        try {
            LimitedTo a = m.getAnnotation(LimitedTo.class);
            if(a==null)
                return this;    // not protected
            else
                return new ProtectedFunction(this,a.value());
        } catch (LinkageError e) {
            // running in JDK 1.4
            return this;
        }
    }

    /**
     * Normal instance methods.
     */
    static final class InstanceFunction extends Function {
        private final Method m;

        public InstanceFunction(Method m) {
            this.m = m;
        }

        public String getName() {
            return m.getName();
        }

        public Class[] getParameterTypes() {
            return m.getParameterTypes();
        }

        Annotation[][] getParameterAnnotatoins() {
            return m.getParameterAnnotations();
        }

        public Object invoke(HttpServletRequest req, Object o, Object... args) throws IllegalAccessException, InvocationTargetException {
            return m.invoke(o,args);
        }
    }

    /**
     * Static methods on the wrapper type.
     */
    static final class StaticFunction extends Function {
        private final Method m;

        public StaticFunction(Method m) {
            this.m = m;
        }

        public String getName() {
            return m.getName();
        }

        public Class[] getParameterTypes() {
            Class[] p = m.getParameterTypes();
            Class[] r = new Class[p.length-1];
            System.arraycopy(p,1,r,0,r.length);
            return r;
        }

        Annotation[][] getParameterAnnotatoins() {
            Annotation[][] a = m.getParameterAnnotations();
            Annotation[][] r = new Annotation[a.length-1][];
            System.arraycopy(a,1,r,0,r.length);
            return r;
        }

        public Object invoke(HttpServletRequest req, Object o, Object... args) throws IllegalAccessException, InvocationTargetException {
            Object[] r = new Object[args.length+1];
            r[0] = o;
            System.arraycopy(args,0,r,1,args.length);
            return m.invoke(null,r);
        }
    }

    /**
     * Function that's protected by
     */
    static final class ProtectedFunction extends Function {
        private final String role;
        private final Function core;

        public ProtectedFunction(Function core, String role) {
            this.role = role;
            this.core = core;
        }

        public String getName() {
            return core.getName();
        }

        public Class[] getParameterTypes() {
            return core.getParameterTypes();
        }

        Annotation[][] getParameterAnnotatoins() {
            return core.getParameterAnnotatoins();
        }

        public Object invoke(HttpServletRequest req, Object o, Object... args) throws IllegalAccessException, InvocationTargetException {
            if(req.isUserInRole(role))
                return core.invoke(req, o, args);
            else
                throw new IllegalAccessException("Needs to be in role "+role);
        }
    }
}
