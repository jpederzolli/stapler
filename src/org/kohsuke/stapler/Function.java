package org.kohsuke.stapler;

import org.apache.commons.io.IOUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.net.URL;

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
     * Gets the list of parameter names.
     */
    abstract String[] getParameterNames();

    /**
     * Use the given arguments as the first N arguments,
     * then figure out the rest of the arguments by looking at parameter annotations,
     * then finally call {@link #invoke}.
     */
    Object bindAndInvoke(Object o, StaplerRequest req, StaplerResponse rsp) throws IllegalAccessException, InvocationTargetException, ServletException {
        Class[] types = getParameterTypes();
        Annotation[][] annotations = getParameterAnnotatoins();
        String[] parameterNames = getParameterNames();

        Object[] arguments = new Object[types.length];

        // find arguments. either known types, or with annotations
        for( int i=0; i<types.length; i++ ) {
            Class t = types[i];
            if(t==StaplerRequest.class || t==HttpServletRequest.class) {
                arguments[i] = req;
                continue;
            }
            if(t==StaplerResponse.class || t==HttpServletResponse.class) {
                arguments[i] = rsp;
                continue;
            }
            arguments[i] = AnnotationHandler.handle(req,annotations[i],
                i<parameterNames.length ? parameterNames[i] : null,
                t);
        }

        return invoke(req,o,arguments);
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

    public abstract <A extends Annotation> A getAnnotation(Class<A> annotation);

    private abstract static class MethodFunction extends Function {
        protected final Method m;
        private volatile String[] names;

        public MethodFunction(Method m) {
            this.m = m;
        }

        public final String getName() {
            return m.getName();
        }

        public final <A extends Annotation> A getAnnotation(Class<A> annotation) {
            return m.getAnnotation(annotation);
        }

        final String[] getParameterNames() {
            if(names==null)
                names = loadParameterNames(m);
            return names;
        }

        private String[] loadParameterNames(Method m) {
                Class<?> c = m.getDeclaringClass();
                URL url = c.getClassLoader().getResource(
                        c.getName().replace('.', '/').replace('$','/') + '/' + m.getName() + ".stapler");
                if(url==null)    return EMPTY_ARRAY;
            try {
                return IOUtils.toString(url.openStream()).split(",");
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load "+url,e);
                return EMPTY_ARRAY;
            }
        }
    }
    /**
     * Normal instance methods.
     */
    static final class InstanceFunction extends MethodFunction {
        public InstanceFunction(Method m) {
            super(m);
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
    static final class StaticFunction extends MethodFunction {
        public StaticFunction(Method m) {
            super(m);
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

        String[] getParameterNames() {
            return core.getParameterNames();
        }

        public Object invoke(HttpServletRequest req, Object o, Object... args) throws IllegalAccessException, InvocationTargetException {
            if(req.isUserInRole(role))
                return core.invoke(req, o, args);
            else
                throw new IllegalAccessException("Needs to be in role "+role);
        }

        public <A extends Annotation> A getAnnotation(Class<A> annotation) {
            return core.getAnnotation(annotation);
        }
    }

    private static final String[] EMPTY_ARRAY = new String[0];
    private static final Logger LOGGER = Logger.getLogger(Function.class.getName());
}
