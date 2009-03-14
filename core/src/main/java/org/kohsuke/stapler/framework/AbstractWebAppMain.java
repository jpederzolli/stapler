package org.kohsuke.stapler.framework;

import org.jvnet.localizer.LocaleProvider;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.framework.errors.NoHomeDirError;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.File;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for web applications.
 *
 * <p>
 * Applications that use stapler can use this as the base class,
 * and then register that as the servlet context listener.
 *
 * @param <T>
 *      The type of the root object instance
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractWebAppMain<T> implements ServletContextListener {

    protected final Class<T> rootType;

    private static final String APP = "app";

    protected ServletContext context;

    /**
     * Once the home directory is determined, this value is set to that directory.
     */
    protected File home;

    protected AbstractWebAppMain(Class<T> rootType) {
        this.rootType = rootType;
    }

    /**
     * Returns the application name, like "Hudson" or "Torricelli".
     *
     * The method should always return the same value. The name
     * should only contain alpha-numeric character.
     */
    protected abstract String getApplicationName();

    /**
     * If the root application object is loaded asynchronously,
     * override this method to return the place holder object
     * to serve the request in the mean time.
     *
     * @return
     *      null to synchronously load the application object.
     */
    protected Object createPlaceHolderForAsyncLoad() {
        return null;
    }

    /**
     * Creates the root application object.
     */
    protected abstract Object createApplication() throws Exception;

    public void contextInitialized(ServletContextEvent event) {
        try {
            context = event.getServletContext();

            installLocaleProvider();

            if (!checkEnvironment())
                return;

            Object ph = createPlaceHolderForAsyncLoad();
            if(ph!=null) {
                // asynchronous load
                context.setAttribute(APP,ph);

                new Thread(getApplicationName()+" initialization thread") {
                    public void run() {
                        setApplicationObject();
                    }
                }.start();
            } else {
                setApplicationObject();
            }
        } catch (Error e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize "+getApplicationName(),e);
            throw e;
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize "+getApplicationName(),e);
            throw e;
        }
    }

    /**
     * Sets the root application object.
     */
    protected void setApplicationObject() {
        try {
            context.setAttribute(APP,createApplication());
        } catch (Error e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize "+getApplicationName(),e);
            throw e;
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize "+getApplicationName(),e);
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize "+getApplicationName(),e);
            throw new Error(e);
        }
    }

    /**
     * Performs pre start-up environment check.
     *
     * @return
     *      false if a check fails. Webapp will fail to boot in this case.
     */
    protected boolean checkEnvironment() {
        home = getHomeDir().getAbsoluteFile();
        home.mkdirs();
        LOGGER.info(getApplicationName()+" home directory: "+home);

        // check that home exists (as mkdirs could have failed silently), otherwise throw a meaningful error
        if (!home.exists()) {
            context.setAttribute(APP,new NoHomeDirError(home));
            return false;
        }

        return true;
    }

    /**
     * Install {@link LocaleProvider} that uses the current request to determine the language.
     */
    private void installLocaleProvider() {
        LocaleProvider.setProvider(new LocaleProvider() {
            public Locale get() {
                Locale locale=null;
                StaplerRequest req = Stapler.getCurrentRequest();
                if(req!=null)
                    locale = req.getLocale();
                if(locale==null)
                    locale = Locale.getDefault();
                return locale;
            }
        });
    }

    /**
     * Determines the home directory for the application.
     *
     * People makes configuration mistakes, so we are trying to be nice
     * with those by doing {@link String#trim()}.
     */
    protected File getHomeDir() {
        // check JNDI for the home directory first
        String varName = getApplicationName().toUpperCase() + "_HOME";
        try {
            InitialContext iniCtxt = new InitialContext();
            Context env = (Context) iniCtxt.lookup("java:comp/env");
            String value = (String) env.lookup(varName);
            if(value!=null && value.trim().length()>0)
                return new File(value.trim());
            // look at one more place. See HUDSON-1314
            value = (String) iniCtxt.lookup(varName);
            if(value!=null && value.trim().length()>0)
                return new File(value.trim());
        } catch (NamingException e) {
            // ignore
        }

        // finally check the system property
        String sysProp = System.getProperty(varName);
        if(sysProp!=null)
            return new File(sysProp.trim());

        // look at the env var next
        String env = System.getenv(varName);
        if(env!=null)
            return new File(env.trim()).getAbsoluteFile();

        return getDefaultHomeDir();
    }

    /**
     * If no home directory is configured, this method is called
     * to determine the default location, which is "~/.appname".
     *
     * Override this method to change that behavior.
     */
    protected File getDefaultHomeDir() {
        return new File(new File(System.getProperty("user.home")),'.'+getApplicationName().toLowerCase());
    }

    public void contextDestroyed(ServletContextEvent event) {
        Object o = event.getServletContext().getAttribute(APP);
        if(rootType.isInstance(o)) {
            cleanUp(rootType.cast(o));
        }
    }

    /**
     * Called during the destructino of the web app to perform
     * any clean up act on the application object.
     */
    protected void cleanUp(T app) {}

    private static final Logger LOGGER = Logger.getLogger(AbstractWebAppMain.class.getName());
}
