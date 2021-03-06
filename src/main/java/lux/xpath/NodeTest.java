package lux.xpath;

import lux.xml.QName;
import lux.xml.ValueType;

public class NodeTest {

    private final ValueType type;

    private final QName name;

    /** @param type the type of test: one of DOCUMENT, NODE, ELEMENT,
     * ATTRIBUTE, TEXT, PROCESSING_INSTRUCTION or COMMENT.
     *
     * @param name the name of the node that the test matches, or null
     * if any node of the given type matches.
     *
     * <p> The local * name may be "*", indicating a match to any local
     * name.
     *
     * <p> The prefix may be "*" to indicate any namespace matches (in
     * which case the namespace uri is ignored and should be null).
     *
     * <p> The namespace uri is preserved for use in query generation,
     * however it is the caller's responsibility to ensure that namespace
     * prefix bindings are consistent within an expression tree, and with
     * the surrounding environment.
     * </p>
     *
     * <p> For node tests without names (node(), comment(), text()), the
     * name is ignored and should be null.  For node tests without namespaces
     * (processing-instruction()), the namespace uri and prefix are both
     * ignored and should be null.
     *
     * @throws IllegalArgumentException if the type is not a node type,
     * @throws NullPointerException if the type is null
     */
    public NodeTest (ValueType type, QName name) {
        if (type.isAtomic) {
            throw new IllegalArgumentException ("Attempt to construct a NodeTest with type " + 
                                                type + " which is not a type of node");
        }
        this.type = type;
        this.name = name;
    }

    public NodeTest (ValueType type) {
        this (type, null);
    }

    public ValueType getType () {
        return type;
    }
    
    public QName getQName() {
        return name;
    }
    
    public boolean isWild () {
        return name == null || name.getLocalPart().equals("*"); // namespace wildcard?
    }

    @Override
    public String toString () {
        StringBuilder buf = new StringBuilder ();
        toString (buf);
        return buf.toString();
    }

    public void toString (StringBuilder buf) {
        if (name == null) {
            buf.append (type.name).append ("()");
            return;
        }
        switch (type) {
        case NODE: case COMMENT: case TEXT:
            buf.append (type.name).append ("()");
            break;
        case ATTRIBUTE:
        case ELEMENT: 
            if ("*".equals(name.getLocalPart())) {
                if (name.getPrefix().isEmpty()) {
                    buf.append (type.name).append ("()");
                } else {
                    buf.append(name.getPrefix()).append(":*");
                }
            } else {
                if ("*".equals(name.getPrefix())) {
                    name.toString(buf);
                } else {
                    buf.append (type.name).append ("(");
                    name.toString(buf);
                    buf.append(')');
                }
            }
            break;
        case DOCUMENT:
            buf.append (type.name).append("(element(");
            name.toString(buf);
            buf.append("))");
            break;
        case PROCESSING_INSTRUCTION:
            buf.append (type.name).append('(').append(name.getLocalPart()).append(')');
            break;
        default:
            throw new IllegalArgumentException ("invalid node type " + type);
        }
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
