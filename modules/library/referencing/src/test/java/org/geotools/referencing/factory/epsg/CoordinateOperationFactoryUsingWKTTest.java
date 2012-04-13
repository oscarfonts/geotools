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

import static org.junit.Assert.*;

import java.io.File;
import java.util.Properties;
import java.util.Set;

import org.geotools.factory.AbstractFactory;
import org.geotools.factory.Hints;
import org.geotools.metadata.iso.citation.Citations;
import org.geotools.referencing.CRS;
import org.geotools.referencing.ReferencingFactoryFinder;
import org.junit.Before;
import org.junit.Test;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.CoordinateOperation;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

/**
 * Tests the {@link testCoordinateOperationFactoryUsingWKT} public methods.
 * 
 * @author Oscar Fonts
 */
public class CoordinateOperationFactoryUsingWKTTest {

    CoordinateOperationFactoryUsingWKT factory;
    private static final String DEFINITIONS_FILE_NAME = "epsg_operations.properties";
    private static final double[] SRC_TEST_POINT = {3.084896111, 39.592654167};
    private static final double[] DST_TEST_POINT = {3.0844689951999427, 39.594235744481225};
    private static Properties properties;
    
    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        ReferencingFactoryFinder.addAuthorityFactory(
                new FactoryUsingWKT(null, AbstractFactory.MAXIMUM_PRIORITY));
        
        factory = (CoordinateOperationFactoryUsingWKT) ReferencingFactoryFinder.
                getCoordinateOperationAuthorityFactory("EPSG",
                new Hints(Hints.COORDINATE_OPERATION_AUTHORITY_FACTORY,
                        CoordinateOperationFactoryUsingWKT.class));

        // Read definitions
        properties = new Properties();
        properties.load(this.getClass().getResourceAsStream(DEFINITIONS_FILE_NAME));
    }
    
    /**
     * @throws Exception
     */
    @Test
    public void testExtraDirectoryHint() throws Exception {
        Hints hints = new Hints(Hints.COORDINATE_OPERATION_AUTHORITY_FACTORY,
                CoordinateOperationFactoryUsingWKT.class);
        try {
           hints.put(Hints.CRS_AUTHORITY_EXTRA_DIRECTORY, "invalid");
           fail("Should of been tossed out as an invalid hint");
        }
        catch (IllegalArgumentException expected) {
            // This is the expected exception.
        }
        String directory = new File(".").getAbsolutePath();
        hints = new Hints(Hints.COORDINATE_OPERATION_AUTHORITY_FACTORY,
                CoordinateOperationFactoryUsingWKT.class);
        hints.put(Hints.CRS_AUTHORITY_EXTRA_DIRECTORY, directory);
        
        CoordinateOperationFactoryUsingWKT fact = (CoordinateOperationFactoryUsingWKT)
            ReferencingFactoryFinder.getCoordinateOperationAuthorityFactory("EPSG",
            new Hints(Hints.COORDINATE_OPERATION_AUTHORITY_FACTORY,
                CoordinateOperationFactoryUsingWKT.class));
        
        CoordinateOperation co = fact.createCoordinateOperation("4326,4230");
        
        CoordinateReferenceSystem crs = CRS.decode("EPSG:4326");
        assertSame(crs, co.getSourceCRS());
        crs = CRS.decode("EPSG:4230");
        assertSame(crs, co.getTargetCRS());

        // Testing BTW the inverse construction
        // (factory is building the 4326->4230 transform from the 4230->4326 definition)
        assertTrue(co.getMathTransform() instanceof MathTransform);
        double[] p = new double[2];
        co.getMathTransform().transform(DST_TEST_POINT, 0, p, 0, 1);
        assertEquals(p[0], SRC_TEST_POINT[0], 1e-8);
        assertEquals(p[1], SRC_TEST_POINT[1], 1e-8);
    }
    
    /**
     * Test method for {@link CoordinateOperationFactoryUsingWKT#getAuthority}.
     */
    @Test
    public void testGetAuthority() {
        assertTrue(factory.getAuthority().equals(Citations.EPSG));
    }
    
    /**
     * Test method for {@link CoordinateOperationFactoryUsingWKT#createCoordinateOperation}.
     * @throws TransformException 
     */
    @Test
    public void testCreateCoordinateOperation() throws TransformException {
        
        String testCode = "nonexistent";
        try {
            factory.createCoordinateOperation(testCode);
            fail();
        } catch (NoSuchAuthorityCodeException e) {
            // This is the expected exception for a bad code
            assertEquals(testCode, e.getAuthorityCode());
        } catch (FactoryException e) {
            fail(factory.getClass().getSimpleName() + " threw a FactoryException when requesting"
              + "a nonexistent operation. Instead, a NoSuchAuthorityCodeException was expected.");
        }

        testCode = "4230,4326";        
        try {
            // Test CoordinateOperation
            CoordinateOperation co = factory.createCoordinateOperation(testCode);
            assertNotNull(co);

            // Test CRSs
            CoordinateReferenceSystem crs = CRS.decode("EPSG:4230");
            assertSame(crs, co.getSourceCRS());
            crs = CRS.decode("EPSG:4326");
            assertSame(crs, co.getTargetCRS());
            
            // Test MathTransform
            assertTrue(co.getMathTransform() instanceof MathTransform);
            double[] p = new double[2];
            co.getMathTransform().transform(SRC_TEST_POINT, 0, p, 0, 1);
            assertEquals(p[0], DST_TEST_POINT[0], 1e-8);
            assertEquals(p[1], DST_TEST_POINT[1], 1e-8);
        } catch (FactoryException e) {
            fail(factory.getClass().getSimpleName() + " threw a FactoryException when creating" +
                    " coordinate operation from an existing code.");            
        }
    }
    
    /**
     * Test method for
     * {@link CoordinateOperationFactoryUsingWKT#createFromCoordinateReferenceSystemCodes}.
     * @throws TransformException 
     */
    @Test
    public void testCreateFromCoordinateReferenceSystemCodes() throws TransformException {
        String testSource = "nonexistent";
        String testTarget = "nonexistent";
        try {
            Set<CoordinateOperation> cos = factory.createFromCoordinateReferenceSystemCodes(
                    testSource, testTarget);
            assertTrue(cos.isEmpty());
        } catch (FactoryException e) {
            fail(factory.getClass().getSimpleName() + " threw a FactoryException when requesting"
                    + "a nonexistent operation. Instead, a NoSuchAuthorityCodeException was expected.");
        }
        
        testSource = "EPSG:4230";
        testTarget = "EPSG:4326";
        try {
            // Test CoordinateOperation
            Set<CoordinateOperation> cos = factory.createFromCoordinateReferenceSystemCodes(testSource, testTarget);
            assertTrue(cos.size() == 1);
            CoordinateOperation co = cos.iterator().next();
            assertNotNull(co);

            // Test CRSs
            CoordinateReferenceSystem crs = CRS.decode(testSource);
            assertSame(crs, co.getSourceCRS());
            crs = CRS.decode(testTarget);
            assertSame(crs, co.getTargetCRS());
            
            // Test MathTransform
            assertTrue(co.getMathTransform() instanceof MathTransform);
            double[] p = new double[2];
            co.getMathTransform().transform(SRC_TEST_POINT, 0, p, 0, 1);
            assertEquals(p[0], DST_TEST_POINT[0], 1e-8);
            assertEquals(p[1], DST_TEST_POINT[1], 1e-8);
        } catch (FactoryException e) {
            fail(factory.getClass().getSimpleName() + " threw a FactoryException when creating" +
                    " coordinate operation from an existing code.");            
        }
    }
}
