package lux.functions;

import java.io.IOException;

import lux.Evaluator;
import lux.index.FieldName;
import lux.index.IndexConfiguration;
import lux.index.field.XmlTextField;
import lux.xpath.FunCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceType;

import org.apache.lucene.index.Fields;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

/**
 * <code>function lux:field-terms($field-name as xs:string?, $start as xs:string?) as xs:anyAtomicItem*</code>
 * <p>
 * This function accepts the name of a Lucene field, and a starting value, and
 * returns the sequence of terms drawn from the field, ordered according to its
 * natural order, starting with the first term that is >= the starting value.
 * </p>
 * <p>
 * If the $field-name argument is empty, the terms are drawn from the default
 * field defined by the {@link IndexConfiguration}, generally the
 * {@link XmlTextField}.
 * </p>
 */
public class FieldTerms extends ExtensionFunctionDefinition {

    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "field-terms");
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] { SequenceType.OPTIONAL_STRING, SequenceType.OPTIONAL_STRING };
    }

    @Override
    public int getMinimumNumberOfArguments() {
        return 0;
    }

    @Override
    public int getMaximumNumberOfArguments() {
        return 2;
    }

    @Override
    public boolean trustResultType() {
        return true;
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.ATOMIC_SEQUENCE;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new FieldTermsCall();
    }

    class FieldTermsCall extends ExtensionFunctionCall {

        @Override
        public SequenceIterator<AtomicValue> call(
                @SuppressWarnings("rawtypes") SequenceIterator<? extends Item>[] arguments, XPathContext context)
                throws XPathException {
            String fieldName = null, start = "";
            if (arguments.length > 0) {
                Item<?> arg0 = arguments[0].next();
                fieldName = arg0.getStringValue();
                if (arguments.length > 1) {
                    Item<?> arg1 = arguments[1].next();
                    start = arg1 == null ? "" : arg1.getStringValue();
                }
            }
            Evaluator eval = SearchBase.getEvaluator(context);
            try {
                if (fieldName == null) {
                    fieldName = eval.getCompiler().getIndexConfiguration().getDefaultFieldName();
                }
                return new TermsIterator(eval, new Term(fieldName, start));
            } catch (IOException e) {
                throw new XPathException("failed getting terms from field " + fieldName, e);
            }
        }

    }

    class TermsIterator implements SequenceIterator<AtomicValue> {
        private TermsEnum terms;
        private final Evaluator eval;
        private Term term;
        private int pos;
        private String current;

        TermsIterator(Evaluator eval, Term term) throws IOException {
            this.term = term;
            this.eval = eval;
            pos = -1;
            createTermsEnum(term);
        }

        private void createTermsEnum(Term t) throws IOException {
            String fieldName;
            if (t != null) {
                fieldName = t.field();
            } else {
                fieldName = eval.getCompiler().getIndexConfiguration().getFieldName(FieldName.XML_TEXT);
            }
            // FIXME: get sub readers (using ReaderUtil (?)) and pull values
            // from those (in parallel?)
            Fields fields = MultiFields.getFields(eval.getSearcher().getIndexReader());
            if (fields != null) {
                terms = fields.terms(fieldName).iterator(null);
                if (t != null) {
                    if (terms.seekCeil(new BytesRef(t.text().getBytes("utf-8"))) == TermsEnum.SeekStatus.END) {
                        pos = -1;
                    } else {
                        current = terms.term().utf8ToString();
                    }
                }
            }
        }

        @Override
        public AtomicValue next() throws XPathException {
            try {
                if (current == null) {
                    return null;
                }
                String value = current;
                BytesRef bytesRef = terms.next();
                if (bytesRef == null) {
                    pos = -1;
                    current = null;
                } else {
                    ++pos;
                    current = bytesRef.utf8ToString();
                }
                return new net.sf.saxon.value.StringValue(value);
            } catch (IOException e) {
                throw new XPathException(e);
            }
        }

        @Override
        public AtomicValue current() {
            return new net.sf.saxon.value.StringValue(current.toString());
        }

        @Override
        public int position() {
            return pos;
        }

        @Override
        public void close() {
        }

        @Override
        public SequenceIterator<AtomicValue> getAnother() throws XPathException {
            try {
                return new TermsIterator(eval, term);
            } catch (IOException e) {
                throw new XPathException(e);
            }
        }

        @Override
        public int getProperties() {
            return 0;
        }

    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
