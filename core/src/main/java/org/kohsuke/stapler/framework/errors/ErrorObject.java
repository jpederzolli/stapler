package org.kohsuke.stapler.framework.errors;

/**
 * Root class of the stapler error objects.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class ErrorObject {
    /**
     * Gets the error message.
     */
    public abstract String getMessage();
}
