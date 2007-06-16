package org.kohsuke.stapler.export;

import com.sun.xml.txw2.TXW;
import com.sun.xml.txw2.output.ResultFactory;
import org.kohsuke.stapler.export.XSD.ComplexType;
import org.kohsuke.stapler.export.XSD.ContentModel;
import org.kohsuke.stapler.export.XSD.Element;
import org.kohsuke.stapler.export.XSD.Schema;

import javax.xml.namespace.QName;
import javax.xml.transform.Result;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/**
 * Generates XML Schema that describes the XML representation of exported beans.
 *
 * @author Kohsuke Kawaguchi
 */
public class SchemaGenerator {
    private final Stack<Model> queue = new Stack<Model>();
    private final Set<Model> written = new HashSet<Model>();
    /**
     * Enumerations to be generated.
     */
    private final Set<Class> enums = new HashSet<Class>();

    private final ModelBuilder builder;
    private final Model<?> top;

    public SchemaGenerator(Model<?> m) {
        this.builder = m.parent;
        this.top = m;
    }

    /**
     * Generates the complete schema to the specified result.
     */
    public void generateSchema(Result r) {
        Schema s = TXW.create(Schema.class, ResultFactory.createSerializer(r));
        s._namespace("xsd",XSD.URI);

        queue.clear();
        written.clear();

        // element decl for the root element
        s.element().name(top.type.getName()).type(getXmlTypeName(top.type));

        // write all beans
        while(!queue.isEmpty())
            writeBean(s, (Model<?>) queue.pop());

        // then enums
        for (Class e : enums)
            writeEnum(s,e);

        s.commit();
    }

    private void writeEnum(Schema s, Class e) {
        XSD.Restriction facets = s.simpleType().name(e.getName()).restriction().base(XSD.Types.STRING);
        for (Object constant : e.getEnumConstants()) {
            facets.enumeration().value(constant.toString());
        }
    }

    private void writeBean(Schema s, Model<?> m) {
        ComplexType ct = s.complexType().name(m.type.getName());
        final ContentModel cm;

        if(m.superModel==null)
            cm = ct.sequence();
        else
            cm = ct.complexContent().extension().base(new QName(m.superModel.type.getName())).sequence();

        for (Property p : m.getProperties()) {
            Class t = p.getType();
            final boolean isCollection;
            final Class itemType;
            if(t.isArray()) {
                isCollection = true;
                itemType = t.getComponentType();
            } else
            if(Collection.class.isAssignableFrom(t)) {
                isCollection = true;
                itemType = TypeUtil.erasure(
                    TypeUtil.getTypeArgument(TypeUtil.getBaseClass(p.getGenericType(),Collection.class),0));
            } else {
                isCollection = false;
                itemType = t;
            }

            Element e = cm.element().name(p.name).type(getXmlTypeName(itemType));
            if(!t.isPrimitive())
                e.minOccurs(0);
            if(isCollection)
                e.maxOccurs("unbounded");
        }
    }

    /**
     * Adds the schema for the XML representation of the given class.
     */
    public void add(Class c) {
        addToQueue(builder.get(c));
    }

    private void addToQueue(Model m) {
        if (written.add(m))
            queue.push(m);
    }

    public QName getXmlTypeName(Class<?> t) {
        if(Property.STRING_TYPES.contains(t))       return XSD.Types.STRING;
        if(t==Boolean.class || t==boolean.class)    return XSD.Types.BOOLEAN;
        if(t==Integer.class || t==int.class)        return XSD.Types.INT;
        if(t==Long.class || t==long.class)          return XSD.Types.LONG;
        if(Map.class.isAssignableFrom(t))           return XSD.Types.ANYTYPE;
        if(Calendar.class.isAssignableFrom(t))      return XSD.Types.LONG;
        if(Enum.class.isAssignableFrom(t)) {
            enums.add(t);
            return new QName(t.getName());
        }

        // otherwise bean
        addToQueue(builder.get(t));
        return new QName(t.getName());
    }
}
