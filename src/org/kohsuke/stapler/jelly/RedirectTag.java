package org.kohsuke.stapler.jelly;

import org.apache.commons.jelly.JellyTagException;
import org.apache.commons.jelly.TagSupport;
import org.apache.commons.jelly.XMLOutput;
import org.kohsuke.stapler.Stapler;

import java.io.IOException;

/**
 * Sends HTTP redirect.
 * 
 * @author Kohsuke Kawaguchi
 */
public class RedirectTag extends TagSupport {
    private String url;

    /**
     * Sets the target URL to redirect to.
     */
    public void setUrl(String url) {
        this.url = url;
    }

    public void doTag(XMLOutput output) throws JellyTagException {
        try {
            Stapler.getCurrentResponse().sendRedirect2(url);
        } catch (IOException e) {
            throw new JellyTagException("Failed to redirect to "+url,e);
        }
    }
}

