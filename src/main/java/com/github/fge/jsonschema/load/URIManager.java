/*
 * Copyright (c) 2013, Francis Galiegue <fgaliegue@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Lesser GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Lesser GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.fge.jsonschema.load;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.github.fge.jackson.JacksonUtils;
import com.github.fge.jsonschema.exceptions.ProcessingException;
import com.github.fge.jsonschema.load.configuration.LoadingConfiguration;
import com.github.fge.jsonschema.messages.MessageBundle;
import com.github.fge.jsonschema.messages.MessageBundles;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;

/**
 * Class to fetch JSON documents
 *
 * <p>This uses a map of {@link URIDownloader} instances to fetch the contents
 * of a URI as an {@link InputStream}, then tries and turns this content into
 * JSON using an {@link ObjectMapper}.</p>
 *
 * <p>Normally, you will never use this class directly.</p>
 *
 * @see DefaultDownloadersDictionary
 * @see SchemaLoader
 */
public final class URIManager
{
    private static final MessageBundle BUNDLE
        = MessageBundles.REF_PROCESSING;
    private static final ObjectReader READER = JacksonUtils.getReader();

    private final Map<String, URIDownloader> downloaders;

    private final Map<URI, URI> schemaRedirects;

    public URIManager()
    {
        this(LoadingConfiguration.byDefault());
    }

    public URIManager(final LoadingConfiguration cfg)
    {
        downloaders = cfg.getDownloaders().entries();
        schemaRedirects = cfg.getSchemaRedirects();
    }

    /**
     * Get the content at a given URI as a {@link JsonNode}
     *
     * @param uri the URI
     * @return the content
     * @throws ProcessingException scheme is not registered, failed to get
     * content, or content is not JSON
     */
    public JsonNode getContent(final URI uri)
        throws ProcessingException
    {
        Preconditions.checkNotNull(uri, "null URI");

        final URI target = schemaRedirects.containsKey(uri)
            ? schemaRedirects.get(uri) : uri;

        if (!target.isAbsolute())
            throw new ProcessingException(BUNDLE.message("uriNotAbsolute")
                .put("uri", uri));

        final String scheme = target.getScheme();

        final URIDownloader downloader = downloaders.get(scheme);

        if (downloader == null)
            throw new ProcessingException(BUNDLE.message("unhandledScheme")
                .put("uri", uri).put("scheme", scheme));

        final InputStream in;

        try {
            in = downloader.fetch(target);
            return READER.readTree(in);
        } catch (JsonProcessingException e) {
            throw new ProcessingException(BUNDLE.message("uriNotJson")
                .put("uri", uri).put("parsingMessage", e.getOriginalMessage()));
        } catch (IOException e) {
            throw new ProcessingException(BUNDLE.message("uriIOError")
                .put("uri", uri).put("exceptionMessage", e.getMessage()));
        }
    }
}
