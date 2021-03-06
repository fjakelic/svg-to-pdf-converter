package com.johnzeringue.svgtopdf.handlers;

import com.johnzeringue.svgtopdf.Fonts;
import com.johnzeringue.svgtopdf.objects.DirectObject;
import com.johnzeringue.svgtopdf.objects.StreamObject;
import java.awt.Font;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * An ElementHandler for Text elements
 *
 * @author John Zeringue
 * @version 05/29/2013
 */
public class TextElementHandler extends ElementHandler {

    private static final String WSP = "\\s";
    private static final String WSPS = WSP + "*";
    private static final String COMMA_WSP = WSPS + "," + WSPS;
    private static final String NUMBER = "(-?\\d+(?:\\.\\d+)?)";
    private static final String TRANSLATE_PATTERN =
            "translate" + WSPS + "\\(" + WSPS + NUMBER + COMMA_WSP + NUMBER
            + WSPS + "\\)" + WSPS;
    private static final String ROTATE_PATTERN =
            "rotate" + WSPS + "\\(" + WSPS + NUMBER + WSPS + "\\)" + WSPS;
    private StreamObject _object;
    private StringBuilder _tagContents;
    private Pattern _translatePattern;
    private Pattern _rotatePattern;

    public TextElementHandler() {
        super();
        _tagContents = new StringBuilder();
        _object = new StreamObject();
        _translatePattern = Pattern.compile(TRANSLATE_PATTERN);
        _rotatePattern = Pattern.compile(ROTATE_PATTERN);
    }

    /**
     * Parser calls this for each element in the document. This will process
     * element and either ignore it, store its data, or write it to the PDF
     * file.
     *
     * @param namespaceURI
     * @param localName
     * @param qName
     * @param atts
     * @throws SAXException
     */
    @Override
    public void startElement() {

        _object.append("q");

        _object.append("BT");

        parseTransform();

        String fontFamily = getValue("font-family").replaceAll("'", "");
        Double fontSize = getValueAsDouble("font-size");
        Font font = new Font(fontFamily,
                getValue("font-weight").equals("bold") ? Font.BOLD : Font.PLAIN,
                (int) Math.round(fontSize));
        
        _object.append("0.0 g");
        _object.append(String.format("%s %.1f Tf",
                Fonts.getInstance().getFontTag(font), fontSize));

        double height = getValueAsDouble("pageHeight");

        _object.append(String.format("%f %f Td",
                getValueAsDouble("x"), height - getValueAsDouble("y")));
    }

    @Override
    public void characters(char ch[], int start, int length) {
        _tagContents.append(ch, start, length);
    }

    /**
     * Parser calls this for each element in the document.
     *
     * @param namespaceURI
     * @param localName
     * @param qName
     * @throws SAXException
     */
    @Override
    public void endElement() {
        int start = 0;
        for (int i = 0; i < _tagContents.length(); i++) {
            if (_tagContents.charAt(i) > 127) {
                _object.append(String.format("(%s) Tj", _tagContents.substring(start, i)));

                if (_tagContents.charAt(i) > 657) {
                    _object.append("q");

                    Double fontSize = getValueAsDouble("font-size");
                    _object.append(String.format("%s %.1f Tf",
                            Fonts.getInstance().getFontTag("Symbol"), fontSize));

                    if ((int) _tagContents.charAt(i) == 955) { // lambda
                        _object.append(String.format("(%s) Tj", (char) 108));
                    } else if ((int) _tagContents.charAt(i) == 963) { // sigma
                        _object.append(String.format("(%s) Tj", (char) 115));
                    }

                    _object.append("Q");
                    _tagContents.insert(i + 1, "  ");
                }

                if ((int) _tagContents.charAt(i) == 177) { // plus minus
                    _object.append("(\\261) Tj");
                }

                start = i + 1;
            }
        }

        _object.append(String.format("(%s) Tj", _tagContents.substring(start)));
        _object.append("ET");
        _object.append("Q");
    }

    private void parseTransform() {
        String transform = getValue("transform");

        while (transform != null && !transform.equals("")) {
            switch (transform.charAt(0)) {
                case 'r':
                    transform = parseRotate(transform);
                    break;
                case 't':
                    transform = parseTranslate(transform);
                    break;
            }
        }
    }

    private String parseRotate(String s) {
        Matcher m = _rotatePattern.matcher(s);

        if (m.lookingAt()) {
            double rotateAngle = -1 * Double.parseDouble(m.group(1)) * Math.PI / 180;

            _object.append(
                    String.format("%f %f %f %f %f %f cm",
                    1.0, 0.0, 0.0, 1.0, 0.0, getValueAsDouble("pageHeight")));
            _object.append(
                    String.format("%f %f %f %f %f %f cm",
                    Math.cos(rotateAngle), Math.sin(rotateAngle),
                    -1 * Math.sin(rotateAngle), Math.cos(rotateAngle),
                    0.0, 0.0));
            _object.append(
                    String.format("%f %f %f %f %f %f cm",
                    1.0, 0.0, 0.0, 1.0, 0.0, -1 * getValueAsDouble("pageHeight")));

            return s.substring(m.end());
        } else {
            throw new IllegalArgumentException("Unrecognized rotate format");
        }
    }

    private String parseTranslate(String s) {
        Matcher m = _translatePattern.matcher(s);

        if (m.lookingAt()) {
            double tx = Double.parseDouble(m.group(1));
            double ty = -1 * Double.parseDouble(m.group(2));

            _object.append(
                    String.format("%f %f %f %f %f %f cm",
                    1.0, 0.0, 0.0, 1.0, tx, ty));

            return s.substring(m.end());
        } else {
            throw new IllegalArgumentException("Unrecognized translate format");
        }
    }

    @Override
    public DirectObject getDirectObject() {
        return _object;
    }

    @Override
    public boolean hasDirectObject() {
        return true;
    }
}
