package org.apache.maven.xml.filters;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.apache.maven.xml.SAXEvent;
import org.apache.maven.xml.SAXEventFactory;
import org.apache.maven.xml.SAXEventUtils;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * <p>
 * Transforms relativePath to version.
 * </p>
 * 
 * @author Robert Scholte
 */
public class ParentXMLFilter
    extends XMLFilterImpl
{
    private boolean parsingParent = false;

    // states
    private static final int OTHER = 0;

    private static final int RELATIVEPATH = 1;

    private int state;

    /**
     * If parent has no version-element, rewrite relativePath to version.<br>
     * If parent has version-element, then remove relativePath.<br>
     * Order of elements must stay the same.
     */
    private boolean hasVersion;

    private String resolvedVersion;

    private List<SAXEvent> saxEvents = new ArrayList<>();

    private SAXEventFactory eventFactory;

    private final Function<String, String> relativePathMapper;

    public ParentXMLFilter( Function<String, String> relativePathMapper )
    {
        this.relativePathMapper = relativePathMapper;
    }

    private SAXEventFactory getEventFactory()
    {
        if ( eventFactory == null )
        {
            eventFactory = SAXEventFactory.newInstance( getContentHandler() );
        }
        return eventFactory;
    }

    private void processEvent( final SAXEvent event )
        throws SAXException
    {
        if ( parsingParent )
        {
            final int eventState = state;

            saxEvents.add( () -> 
            {
                if ( !( eventState == RELATIVEPATH && hasVersion ) )
                {
                    event.execute();
                }
            } );
        }
        else
        {
            event.execute();
        }
    }

    @Override
    public void startElement( String uri, String localName, String qName, Attributes atts )
        throws SAXException
    {
        if ( !parsingParent && "parent".equals( localName ) )
        {
            parsingParent = true;
        }

        if ( parsingParent )
        {
            if ( "relativePath".equals( localName ) )
            {
                state = RELATIVEPATH;
                processEvent( () -> 
                {
                    if ( resolvedVersion != null )
                    {
                        String versionQName = SAXEventUtils.renameQName( qName, "version" );

                        getEventFactory().startElement( uri, "version", versionQName, null ).execute();
                    }
                    else
                    {
                        getEventFactory().startElement( uri, localName, qName, atts ).execute();
                    }
                } );
                return;
            }
            else
            {
                state = OTHER;
            }

            if ( "version".equals( localName ) )
            {
                hasVersion = true;
            }
            processEvent( getEventFactory().startElement( uri, localName, qName, atts ) );
        }
        else
        {
            super.startElement( uri, localName, qName, atts );
        }
    }

    @Override
    public void characters( char[] ch, int start, int length )
        throws SAXException
    {
        if ( parsingParent && state == RELATIVEPATH )
        {
            String relativePath = new String( ch, start, length );
            resolvedVersion = relativePathToVersion( relativePath );

            processEvent( () -> 
            {
                if ( resolvedVersion != null )
                {
                    getEventFactory().characters( resolvedVersion.toCharArray(), 0,
                                                  resolvedVersion.length() ).execute();
                }
                else
                {
                    getEventFactory().characters( ch, start, length ).execute();
                }
            } );
        }
        else
        {
            processEvent( getEventFactory().characters( ch, start, length ) );
        }

    }

    @Override
    public void endDocument()
        throws SAXException
    {
        processEvent( getEventFactory().endDocument() );
    }

    @Override
    public void endElement( String uri, String localName, String qName )
        throws SAXException
    {
        if ( !parsingParent )
        {
            super.endElement( uri, localName, qName );
        }
        else if ( "relativePath".equals( localName ) )
        {
            processEvent( () -> 
            {
                if ( resolvedVersion != null )
                {
                    String versionQName = SAXEventUtils.renameQName( qName, "version" );
                    getEventFactory().endElement( uri, "version", versionQName ).execute();
                }
                else
                {
                    getEventFactory().endElement( uri, localName, qName ).execute();
                }
            } );
        }
        else
        {
            if ( "parent".equals( localName ) )
            {
                // not with streams due to checked SAXException
                for ( SAXEvent saxEvent : saxEvents )
                {
                    saxEvent.execute();
                }
                parsingParent = false;
            }
            processEvent( getEventFactory().endElement( uri, localName, qName ) );
        }
    }

    @Override
    public void endPrefixMapping( String prefix )
        throws SAXException
    {
        processEvent( getEventFactory().endPrefixMapping( prefix ) );
    }

    @Override
    public void ignorableWhitespace( char[] ch, int start, int length )
        throws SAXException
    {
        processEvent( getEventFactory().ignorableWhitespace( ch, start, length ) );
    }

    @Override
    public void processingInstruction( String target, String data )
        throws SAXException
    {
        processEvent( getEventFactory().processingInstruction( target, data ) );

    }

    @Override
    public void setDocumentLocator( Locator locator )
    {
        try
        {
            processEvent( getEventFactory().setDocumentLocator( locator ) );
        }
        catch ( SAXException e )
        {
            // noop
        }
    }

    @Override
    public void skippedEntity( String name )
        throws SAXException
    {
        processEvent( getEventFactory().skippedEntity( name ) );
    }

    @Override
    public void startDocument()
        throws SAXException
    {
        processEvent( getEventFactory().startDocument() );
    }

    @Override
    public void startPrefixMapping( String prefix, String uri )
        throws SAXException
    {
        processEvent( getEventFactory().startPrefixMapping( prefix, uri ) );
    }

    protected String relativePathToVersion( String relativePath )
    {
        return relativePathMapper.apply( relativePath );
    }
}
