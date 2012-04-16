/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2002-2012, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotools.referencing.factory.epsg;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.geotools.factory.GeoTools;
import org.geotools.factory.Hints;
import org.geotools.metadata.iso.citation.Citations;
import org.geotools.referencing.factory.AbstractAuthorityFactory;
import org.geotools.referencing.factory.DeferredAuthorityFactory;
import org.geotools.referencing.factory.FactoryNotFoundException;
import org.geotools.referencing.factory.PropertyCoordinateOperationAuthorityFactory;
import org.geotools.referencing.factory.ReferencingFactoryContainer;
import org.geotools.resources.i18n.ErrorKeys;
import org.geotools.resources.i18n.Errors;
import org.geotools.resources.i18n.LoggingKeys;
import org.geotools.resources.i18n.Loggings;
import org.geotools.util.logging.Logging;
import org.opengis.metadata.Identifier;
import org.opengis.metadata.citation.Citation;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.CoordinateOperation;
import org.opengis.referencing.operation.CoordinateOperationAuthorityFactory;

/**
 * Authority factory that holds user-defined
 * {@linkplain CoordinateOperation Coordinate Operations}.
 * <p>
 * This factory can be used as a replacement for Coordinate Operations when there is no access
 * to a complete EPSG database. Or can be used to override the coordinate operations defined in
 * EPSG if assigned a higher priority.
 * <p>
 * The Coordinate Operations are defined as <cite>Well Known Text</cite> math transforms (see
 * {@link PropertyCoordinateOperationAuthorityFactory} for format specification and examples).
 * <p>
 * Property file name is {@value #FILENAME}, and its possible locations are described
 * {@linkplain #FILENAME here}. If no property file is found, the factory won't be activated.
 * <p>
 * If an operation is not found in the properties file, this factory will delegate
 * creation on a fallback factory. The fallback factory is the next registered
 * {@link CoordinateOperationAuthorityFactory} after this one in the
 * {@linkplain org.geotools.factory.AbstractFactory#priority priority} chain.
 * 
 * @source $URL$
 * @version $Id$
 * @author Oscar Fonts
 */
public class CoordinateOperationFactoryUsingWKT extends DeferredAuthorityFactory
        implements CoordinateOperationAuthorityFactory {
    /**
     * The authority. Will be created only when first needed.
     *
     * @see #getAuthority
     */
    protected Citation authority;
    
    /**
     * The default filename to read. The default {@code FactoryUsingWKT} implementation will
     * search for the first occurence of this file in the following places:
     * <p>
     * <ul>
     *   <li>In the directory specified by the
     *       {@value org.geotools.factory.GeoTools#CRS_AUTHORITY_EXTRA_DIRECTORY}
     *       system property.</li>
     *   <li>In every {@code org/geotools/referencing/factory/espg} directories found on the
     *       classpath.</li>
     * </ul>
     * <p>
     *
     * @see #getDefinitionsURL
     */
    public static final String FILENAME = "epsg_operations.properties";
    
    /**
     * Priority for this factory
     */
    public static final int PRIORITY = NORMAL_PRIORITY - 20;
    
    /**
     * The factories to be given to the backing store.
     */
    protected final ReferencingFactoryContainer factories;
    
    /**
     * Directory scanned for extra definitions.
     */
    protected final String directory;
    
    /**
     * Constructs an authority factory using the default set of factories.
     */
    public CoordinateOperationFactoryUsingWKT() {
        this(null, PRIORITY);
    }
    
    /**
     * Constructs an authority factory using a set of factories created from the specified hints.
     */
    public CoordinateOperationFactoryUsingWKT(Hints userHints) {
        this(userHints, PRIORITY);
    }
    
    /**
     * Constructs an authority factory using the specified hints and priority.
     */
    protected CoordinateOperationFactoryUsingWKT(final Hints userHints, final int priority) {
        super(userHints, priority);
        factories = ReferencingFactoryContainer.instance(userHints);
        
        // Search for user CRS_AUTHORITY_EXTRA_DIRECTORY hint, or use system default value.
        Object directoryHint = null;
        if (userHints != null && userHints.get(Hints.CRS_AUTHORITY_EXTRA_DIRECTORY) != null) {
            directoryHint = userHints.get(Hints.CRS_AUTHORITY_EXTRA_DIRECTORY);
        } else if (Hints.getSystemDefault(Hints.CRS_AUTHORITY_EXTRA_DIRECTORY) != null) {
            directoryHint = Hints.getSystemDefault(Hints.CRS_AUTHORITY_EXTRA_DIRECTORY);
        }
        if (directoryHint != null) {
            directory = directoryHint.toString();
            hints.put(Hints.CRS_AUTHORITY_EXTRA_DIRECTORY, directory);
        } else {
            directory = null;
        }

    }
    
    public synchronized Citation getAuthority() {
        if (authority == null) {
            authority = Citations.EPSG;
        }
        return authority;
    }

    /**
     * Creates the backing store authority factory.
     *
     * @return The backing store to uses in {@code createXXX(...)} methods.
     * @throws FactoryNotFoundException if the {@code properties} file has not been found.
     * @throws FactoryException if the constructor failed to find or read the file.
     *         This exception usually has an {@link IOException} as its cause.
     */
    protected AbstractAuthorityFactory createBackingStore() throws FactoryException {
        try {
            URL url = getDefinitionsURL();
            if (url == null) {
                throw new FactoryNotFoundException(Errors.format(
                        ErrorKeys.FILE_DOES_NOT_EXIST_$1, FILENAME));
            }
            final Iterator<? extends Identifier> ids = getAuthority().getIdentifiers().iterator();
            final String authority = ids.hasNext() ? ids.next().getCode() : "EPSG";
            final LogRecord record = Loggings.format(Level.CONFIG,
                    LoggingKeys.USING_FILE_AS_FACTORY_$2, url.getPath(), authority);
            record.setLoggerName(LOGGER.getName());
            LOGGER.log(record);
            return new PropertyCoordinateOperationAuthorityFactory(factories, this.getAuthority(), url);
        } catch (IOException exception) {
            throw new FactoryException(Errors.format(ErrorKeys.CANT_READ_$1, FILENAME), exception);
        }
    }
    
    /**
     * Returns the URL to the property file that contains Operation definitions.
     * The default implementation performs the following search path:
     * <ul>
     *   <li>If a value is set for the {@value GeoTools#CRS_AUTHORITY_EXTRA_DIRECTORY} system property key,
     *       then the {@value #FILENAME} file will be searched in this directory.</li>
     *   <li>If no value is set for the above-cited system property, or if no {@value #FILENAME}
     *       file was found in that directory, then the first {@value #FILENAME} file found in
     *       any {@code org/geotools/referencing/factory/epsg} directory on the classpath will
     *       be used.</li>
     *   <li>If no file was found on the classpath neither, then this factory will be disabled.</li>
     * </ul>
     *
     * @return The URL, or {@code null} if none.
     */
    protected URL getDefinitionsURL() {
        try {
            if (directory != null) {
                final File file = new File(directory, FILENAME);
                if (file.isFile()) {
                    return file.toURI().toURL(); // TODO
                }
            }
        } catch (SecurityException exception) {
            Logging.unexpectedException(LOGGER, exception);
        } catch (MalformedURLException exception) {
            Logging.unexpectedException(LOGGER, exception);
        }
        return this.getClass().getResource(FILENAME);
    }
}
