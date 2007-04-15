package org.kohsuke.stapler.export;

import java.lang.reflect.Field;

/**
 * {@link Property} based on {@link Field}.
 * @author Kohsuke Kawaguchi
 */
class FieldProperty extends Property {
    private final Field field;

    public FieldProperty(Parser owner, Field field, Exposed exposed) {
        super(owner,field.getName(),exposed);
        this.field = field;
    }

    protected Object getValue(Object object) throws IllegalAccessException {
        return field.get(object);
    }
}
