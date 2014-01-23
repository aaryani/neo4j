/**
 * Copyright (c) 2002-2014 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.api.index;

import java.io.IOException;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import org.neo4j.kernel.impl.api.index.IndexUpdateMode;

import static java.util.Arrays.asList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.neo4j.helpers.collection.IteratorUtil.asSet;
import static org.neo4j.kernel.api.properties.Property.stringProperty;

@Ignore( "Not a test. This is a compatibility suite that provides test cases for verifying" +
        " SchemaIndexProvider implementations. Each index provider that is to be tested by this suite" +
        " must create their own test class extending IndexProviderCompatibilityTestSuite." +
        " The @Ignore annotation doesn't prevent these tests to run, it rather removes some annoying" +
        " errors or warnings in some IDEs about test classes needing a public zero-arg constructor." )
public class UniqueIndexPopulatorCompatibility extends IndexProviderCompatibilityTestSuite.Compatibility
{
    public UniqueIndexPopulatorCompatibility( IndexProviderCompatibilityTestSuite testSuite )
    {
        super( testSuite );
    }

    @Test
    public void shouldProvidePopulatorThatEnforcesUniqueConstraints() throws Exception
    {
        // when
        String value = "value1";
        int nodeId1 = 1;
        int nodeId2 = 2;

        IndexPopulator populator = indexProvider.getPopulator( 17, descriptor, new IndexConfiguration( true ) );
        populator.create();
        populator.add( nodeId1, value );
        populator.add( nodeId2, value );
        try
        {
            PropertyAccessor propertyAccessor = mock( PropertyAccessor.class );
            int propertyKeyId = descriptor.getPropertyKeyId();
            when( propertyAccessor.getProperty( nodeId1, propertyKeyId )).thenReturn(
                    stringProperty( propertyKeyId, value ) );
            when( propertyAccessor.getProperty( nodeId2, propertyKeyId )).thenReturn(
                    stringProperty( propertyKeyId, value ) );

            populator.verifyDeferredConstraints( propertyAccessor );

            fail( "expected exception" );
        }
        // then
        catch ( PreexistingIndexEntryConflictException conflict )
        {
            assertEquals( nodeId1, conflict.getExistingNodeId() );
            assertEquals( value, conflict.getPropertyValue() );
            assertEquals( nodeId2, conflict.getAddedNodeId() );
        }
    }

    @Test
    public void shouldProvideAccessorThatEnforcesUniqueConstraintsAgainstDataAddedOnline() throws Exception
    {
        // given
        IndexPopulator populator = indexProvider.getPopulator( 17, descriptor, new IndexConfiguration( true ) );
        populator.create();
        populator.close( true );

        // when
        IndexAccessor accessor = indexProvider.getOnlineAccessor( 17, new IndexConfiguration( true ) );
        updateAccessor( accessor, asList( NodePropertyUpdate.add( 1, 11, "value1",
                new long[]{4} ) ) );
        try
        {
            updateAccessor( accessor, asList( NodePropertyUpdate.add( 2, 11, "value1", new long[]{4} ) ) );

            fail( "expected exception" );
        }
        // then
        catch ( PreexistingIndexEntryConflictException conflict )
        {
            assertEquals( 1, conflict.getExistingNodeId() );
            assertEquals( "value1", conflict.getPropertyValue() );
            assertEquals( 2, conflict.getAddedNodeId() );
        }
    }

    @Test
    public void shouldProvideAccessorThatEnforcesUniqueConstraintsAgainstDataAddedThroughPopulator() throws Exception
    {
        // given
        IndexPopulator populator = indexProvider.getPopulator( 17, descriptor, new IndexConfiguration( true ) );
        populator.create();
        populator.add( 1, "value1" );
        populator.close( true );

        // when
        IndexAccessor accessor = indexProvider.getOnlineAccessor( 17, new IndexConfiguration( true ) );
        try
        {
            updateAccessor( accessor, asList( NodePropertyUpdate.add( 2, 11, "value1", new long[]{4} ) ) );

            fail( "expected exception" );
        }
        // then
        catch ( PreexistingIndexEntryConflictException conflict )
        {
            assertEquals( 1, conflict.getExistingNodeId() );
            assertEquals( "value1", conflict.getPropertyValue() );
            assertEquals( 2, conflict.getAddedNodeId() );
        }
    }

    @Test
    public void shouldProvideAccessorThatEnforcesUniqueConstraintsAgainstDataAddedInSameTx() throws Exception
    {
        // given
        IndexPopulator populator = indexProvider.getPopulator( 17, descriptor, new IndexConfiguration( true ) );
        populator.create();
        populator.close( true );

        // when
        IndexAccessor accessor = indexProvider.getOnlineAccessor( 17, new IndexConfiguration( true ) );
        try
        {
           updateAccessor( accessor, asList(
                   NodePropertyUpdate.add( 1, 11, "value1", new long[]{4} ),
                   NodePropertyUpdate.add( 2, 11, "value1", new long[]{4} ) ) );

            fail( "expected exception" );
        }
        // then
        catch ( DuplicateIndexEntryConflictException conflict )
        {
            assertEquals( "value1", conflict.getPropertyValue() );
            assertEquals( asSet( 1l, 2l ), conflict.getConflictingNodeIds() );
        }
    }


    private static void updateAccessor( IndexAccessor accessor, List<NodePropertyUpdate> updates )
            throws IOException, IndexEntryConflictException
    {
        try ( IndexUpdater updater = accessor.newUpdater( IndexUpdateMode.ONLINE ) )
        {
            for ( NodePropertyUpdate update : updates )
            {
                updater.process( update );
            }
        }
    }
}