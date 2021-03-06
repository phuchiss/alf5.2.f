/*
 * #%L
 * Alfresco Repository
 * %%
 * Copyright (C) 2005 - 2016 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software. 
 * If the software was purchased under a paid Alfresco license, the terms of 
 * the paid license agreement will prevail.  Otherwise, the software is 
 * provided under the following open source license terms:
 * 
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.repo.content.transform;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Random;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.content.filestore.FileContentReader;
import org.alfresco.repo.content.filestore.FileContentWriter;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.TransformationOptions;
import org.alfresco.util.TempFileProvider;

/**
 * @see org.alfresco.repo.content.transform.StringExtractingContentTransformer
 * 
 * @author Derek Hulley
 */
public class StringExtractingContentTransformerTest extends AbstractContentTransformerTest
{
    private static final String SOME_CONTENT;
    static
    {
        // force the content to be encoded in a particular way, independently of the source file encoding
        try
        {
            SOME_CONTENT = new String("azAz10!???$%^&*()\t\r\n".getBytes("UTF-8"), "MacDingbat");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new AlfrescoRuntimeException("Encoding not recognised", e);
        }
    }
    
    private StringExtractingContentTransformer transformer;
    /** the final destination of transformations */
    private ContentWriter targetWriter;
    
    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        
        transformer = new StringExtractingContentTransformer();
        transformer.setMimetypeService(mimetypeService);
        transformer.setTransformerDebug(transformerDebug);
        transformer.setTransformerConfig(transformerConfig);
        targetWriter = new FileContentWriter(getTempFile());
        targetWriter.setMimetype("text/plain");
        targetWriter.setEncoding("UTF-8");
    }
    
    protected ContentTransformer getTransformer(String sourceMimetype, String targetMimetype)
    {
        return transformer;
    }

    public void testSetUp() throws Exception
    {
        assertNotNull(transformer);
    }
    
    /**
     * @return Returns a new temp file
     */
    private File getTempFile()
    {
        return TempFileProvider.createTempFile(getName(), ".txt");
    }
    
    /**
     * Writes some content using the mimetype and encoding specified.
     * 
     * @param mimetype String
     * @param encoding String
     * @return Returns a reader onto the newly written content
     */
    private ContentReader writeContent(String mimetype, String encoding)
    {
        ContentWriter writer = new FileContentWriter(getTempFile());
        writer.setMimetype(mimetype);
        writer.setEncoding(encoding);
        // put content
        writer.putContent(SOME_CONTENT);
        // return a reader onto the new content
        return writer.getReader();
    }
    
    public void testDirectTransform() throws Exception
    {
        ContentReader reader = writeContent("text/plain", "MacDingbat");
        
        // check transformability
        assertTrue(transformer.isTransformable(reader.getMimetype(), -1, targetWriter.getMimetype(), new TransformationOptions()));
        
        // transform
        transformer.transform(reader, targetWriter);
        
        // get a reader onto the transformed content and check
        ContentReader checkReader = targetWriter.getReader();
        String checkContent = checkReader.getContentString();
        assertEquals("Content check failed", SOME_CONTENT, checkContent);
    }
    
    public void testInterTextTransform() throws Exception
    {
        ContentReader reader = writeContent("text/xml", "MacDingbat");
        
        // check transformability
        assertTrue(transformer.isTransformable(reader.getMimetype(), -1, targetWriter.getMimetype(), new TransformationOptions()));
        
        // transform
        transformer.transform(reader, targetWriter);
        
        // get a reader onto the transformed content and check
        ContentReader checkReader = targetWriter.getReader();
        String checkContent = checkReader.getContentString();
        assertEquals("Content check failed", SOME_CONTENT, checkContent);
    }
    
    /**
     * Generate a large file and then transform it using the text extractor.
     * We are not creating super-large file (1GB) in order to test the transform
     * as it takes too long to create the file in the first place.  Rather,
     * this test can be used during profiling to ensure that memory is not
     * being consumed.
     */
    public void testLargeFileStreaming() throws Exception
    {
        File sourceFile = TempFileProvider.createTempFile(getName(), ".txt");
        
        int chars = 1000000;  // a million characters should do the trick
        Random random = new Random();
        
        Writer charWriter = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(sourceFile)));
        for (int i = 0; i < chars; i++)
        {
            char next = (char)(random.nextDouble() * 93D + 32D);
            charWriter.write(next);
        }
        charWriter.close();
        
        // get a reader and a writer
        ContentReader reader = new FileContentReader(sourceFile);
        reader.setMimetype(MimetypeMap.MIMETYPE_TEXT_PLAIN);
        
        File outputFile = TempFileProvider.createTempFile(getName(), ".txt");
        ContentWriter writer = new FileContentWriter(outputFile);
        writer.setMimetype(MimetypeMap.MIMETYPE_TEXT_PLAIN);
        
        // transform
        transformer.transform(reader, writer);
        
        // delete files
        sourceFile.delete();
        outputFile.delete();
    }
}
