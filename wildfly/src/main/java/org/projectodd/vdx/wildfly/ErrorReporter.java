/*
 * Copyright 2016 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.projectodd.vdx.wildfly;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.projectodd.vdx.core.ErrorPrinter;
import org.projectodd.vdx.core.ErrorType;
import org.projectodd.vdx.core.I18N;
import org.projectodd.vdx.core.Printer;
import org.projectodd.vdx.core.Stringifier;
import org.projectodd.vdx.core.Util;
import org.projectodd.vdx.core.ValidationError;
import org.projectodd.vdx.core.XMLStreamValidationException;

public abstract class ErrorReporter {
    public ErrorReporter(final URL document) {
        this.document = document;
    }

    /**
     * Reports an error to VDX.
     * @param exception
     * @return true if the error was actually printed
     */
    public boolean report(final XMLStreamException exception) {
        boolean printed = false;
        try {
            final List<URL> schemas = findSchemas();

            if (!schemas.isEmpty()) {
                final ValidationError error;
                if (exception instanceof XMLStreamValidationException) {
                    error = ((XMLStreamValidationException) exception).getValidationError();
                } else {
                    final String message = exception.getMessage();

                    final Optional<String> dupAttribute = duplicateAttribute(message);

                    if (dupAttribute.isPresent()) {
                        error = ValidationError.from(exception, ErrorType.DUPLICATE_ATTRIBUTE)
                                .attribute(QName.valueOf(dupAttribute.get()));
                    } else {
                        error = ValidationError.from(exception, ErrorType.UNKNOWN_ERROR);
                        final Optional<String> strippedMessage = stripMessageCode(message);
                        if (strippedMessage.isPresent()) {
                            error.fallbackMessage(strippedMessage.get());
                        }
                    }
                }

                final List<Stringifier> stringifiers = new ArrayList<>();
                stringifiers.add(new SubsystemStringifier());

                SchemaDocRelationships rel = new SchemaDocRelationships();

               final ErrorPrinter errPrinter = new ErrorPrinter(this.document, schemas)
                       .printer(printer())
                       .stringifiers(stringifiers)
                       .pathGate(rel)
                       .prefixProvider(rel);

                if (errPrinter.documentHasContent()) {
                    errPrinter.print(error);
                    printed = true;
                } else {
                    printer().println(I18N.documentHasNoContent(Util.documentName(document)));
                }
            }
        } catch (Exception ex) {
            printer().println(I18N.failedToPrintError(ex));
        }

        return printed;
    }

    public static Optional<String> duplicateAttribute(final String msg) {
        // detect duplicate attribute - this message comes from woodstox, and isn't i18n, so we don't have to
        // worry about other languages
        final Matcher dupMatcher = Pattern.compile("^Duplicate attribute '(.+?)'\\.").matcher(msg);
        if (dupMatcher.find()) {

            return Optional.of(dupMatcher.group(1));
        }

        return Optional.empty();
    }

    public static Optional<String> stripMessageCode(final String msg) {
        // match Open/OracleJDK messages
        Matcher m = Pattern.compile("Message: \"?([A-Z]+\\d+: )?(.*?)\"?$").matcher(msg);
        if (m.find()) {

            return Optional.of(m.group(2));
        }

        // match IBM JDK messages
        m = Pattern.compile("^\"?([A-Z]+\\d+: )?(.*?)\"?$").matcher(msg);
        if (m.find()) {

            return Optional.of(m.group(2));
        }

        return Optional.empty();
    }

    protected List<URL> findSchemas() {
        final SchemaProvider provider = schemaProvider();
        final List<URL> schemas = provider.schemas();

        if (schemas.isEmpty()) {
            printer().println(I18N.noSchemasAvailable(provider.schemaResource()));
        }

        return schemas;
    }

    protected abstract SchemaProvider schemaProvider();

    protected abstract Printer printer();

    private final URL document;
}
