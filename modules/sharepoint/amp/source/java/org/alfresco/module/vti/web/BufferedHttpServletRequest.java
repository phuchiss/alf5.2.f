/*
 * #%L
 * Alfresco Sharepoint Protocol
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
package org.alfresco.module.vti.web;

import java.io.IOException;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.apache.chemistry.opencmis.commons.server.TempStoreOutputStream;
import org.apache.chemistry.opencmis.server.shared.TempStoreOutputStreamFactory;
import org.springframework.util.FileCopyUtils;

/**
 * Buffered wrapper for request.  
 * 
 * @author alex.mukha
 * @since 4.2.3
 */
public class BufferedHttpServletRequest extends HttpServletRequestWrapper
{
    private TempStoreOutputStreamFactory streamFactory;
    private HttpServletRequest request;
    private TempStoreOutputStream bufferStream;
    private ServletInputStream contentStream;
    
    public BufferedHttpServletRequest(HttpServletRequest request, TempStoreOutputStreamFactory streamFactory)
    {
        super(request);
        this.request = request;
        this.streamFactory = streamFactory;
    }

    private void bufferInputStream() throws IOException
    {
        TempStoreOutputStream bufferStream = streamFactory.newOutputStream();

        try
        {
            FileCopyUtils.copy(request.getInputStream(), bufferStream);
        }
        catch (IOException e)
        {
            bufferStream.destroy(e); // remove temp file
            throw e;
        }
        this.bufferStream = bufferStream;
    }
    
    @Override
    public ServletInputStream getInputStream() throws IOException
    {
        if (bufferStream == null)
        {
            try
            {
                bufferInputStream();
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
        contentStream = new AlfrescoServletInputStream(bufferStream.getInputStream());
        return contentStream;
    }
    
    public void close()
    {
        if (contentStream != null)
        {
            try
            {
                contentStream.reset();
            }
            catch (IOException ioe)
            {

            }
            contentStream = null;
        }
        
        if (bufferStream != null)
        {
            try
            {
                bufferStream.close();
            }
            catch (IOException ioe)
            {

            }
            bufferStream.destroy(null);
            bufferStream = null;
        }
    }
}
