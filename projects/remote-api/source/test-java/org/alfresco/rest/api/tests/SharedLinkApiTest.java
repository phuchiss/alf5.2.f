/*
 * #%L
 * Alfresco Remote API
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
package org.alfresco.rest.api.tests;

import static org.alfresco.rest.api.tests.util.RestApiUtil.toJsonAsStringNonNull;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.quickshare.QuickShareLinkExpiryActionImpl;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.tenant.TenantUtil;
import org.alfresco.rest.api.People;
import org.alfresco.rest.api.QuickShareLinks;
import org.alfresco.rest.api.impl.QuickShareLinksImpl;
import org.alfresco.rest.api.model.QuickShareLink;
import org.alfresco.rest.api.nodes.NodesEntityResource;
import org.alfresco.rest.api.quicksharelinks.QuickShareLinkEntityResource;
import org.alfresco.rest.api.tests.client.HttpResponse;
import org.alfresco.rest.api.tests.client.PublicApiClient.Paging;
import org.alfresco.rest.api.tests.client.data.Document;
import org.alfresco.rest.api.tests.client.data.Node;
import org.alfresco.rest.api.tests.client.data.QuickShareLinkEmailRequest;
import org.alfresco.rest.api.tests.client.data.Rendition;
import org.alfresco.rest.api.tests.client.data.UserInfo;
import org.alfresco.rest.api.tests.util.MultiPartBuilder;
import org.alfresco.rest.api.tests.util.RestApiUtil;
import org.alfresco.service.cmr.action.scheduled.ScheduledPersistedActionService;
import org.alfresco.service.cmr.quickshare.QuickShareLinkExpiryAction;
import org.alfresco.service.cmr.quickshare.QuickShareLinkExpiryActionPersister;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * V1 REST API tests for Shared Links (aka public "quick shares")
 *
 * <ul>
 * <li> {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/shared-links} </li>
 * <li> {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/shared-links/<sharedId>} </li>
 * </ul>
 *
 * @author janv
 */
public class SharedLinkApiTest extends AbstractBaseApiTest
{
    private static final String URL_SHARED_LINKS = "shared-links";

    private QuickShareLinkExpiryActionPersister quickShareLinkExpiryActionPersister;
    private ScheduledPersistedActionService scheduledPersistedActionService;

    @Before
    public void setup() throws Exception
    {
        super.setup();
        quickShareLinkExpiryActionPersister = applicationContext.getBean("quickShareLinkExpiryActionPersister", QuickShareLinkExpiryActionPersister.class);
        scheduledPersistedActionService = applicationContext.getBean("scheduledPersistedActionService", ScheduledPersistedActionService.class);
    }

    /**
     * Tests shared links to file (content)
     *
     * <p>POST:</p>
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/shared-links}
     *
     * <p>DELETE:</p>
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/shared-links/<sharedId>}
     *
     * <p>GET:</p>
     * The following do not require authentication
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/shared-links/<sharedId>}
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/shared-links/<sharedId>/content}
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/shared-links/<sharedId>/renditions}
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/shared-links/<sharedId>/renditions/<renditionId>}
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/shared-links/<sharedId>/renditions/<renditionId>/content}
     *
     */
    @Test
    public void testSharedLinkCreateGetDelete() throws Exception
    {
        // As user 1 ...
        setRequestContext(user1);

        // create doc d1 - pdf
        String sharedFolderNodeId = getSharedNodeId();

        String fileName1 = "quick"+RUNID+"_1.pdf";
        File file1 = getResourceFile("quick.pdf");

        byte[] file1_originalBytes = Files.readAllBytes(Paths.get(file1.getAbsolutePath()));

        String file1_MimeType = MimetypeMap.MIMETYPE_PDF;

        MultiPartBuilder multiPartBuilder = MultiPartBuilder.create()
                .setFileData(new MultiPartBuilder.FileData(fileName1, file1, file1_MimeType));
        MultiPartBuilder.MultiPartRequest reqBody = multiPartBuilder.build();

        HttpResponse response = post(getNodeChildrenUrl(sharedFolderNodeId), reqBody.getBody(), null, reqBody.getContentType(), 201);
        Document doc1 = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), Document.class);

        String d1Id = doc1.getId();

        // create doc d2 - plain text
        String myFolderNodeId = getMyNodeId();

        String content2Text = "The quick brown fox jumps over the lazy dog 2.";
        String fileName2 = "content" + RUNID + "_2.txt";

        Document doc2 = createTextFile(myFolderNodeId, fileName2, content2Text);
        String d2Id = doc2.getId();

        String file2_MimeType = MimetypeMap.MIMETYPE_TEXT_PLAIN;

        // As user 2 ...
        setRequestContext(user2);

        response = getSingle(NodesEntityResource.class, d1Id, null, 200);
        Node nodeResp = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), Node.class);
        Date docModifiedAt = nodeResp.getModifiedAt();
        String docModifiedBy = nodeResp.getModifiedByUser().getId();
        assertEquals(user1, docModifiedBy);

        // create shared link to document 1
        Map<String, String> body = new HashMap<>();
        body.put("nodeId", d1Id);

        response = post(URL_SHARED_LINKS, toJsonAsStringNonNull(body), 201);
        QuickShareLink resp = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), QuickShareLink.class);

        String shared1Id = resp.getId();
        assertNotNull(shared1Id);

        assertEquals(d1Id, resp.getNodeId());
        assertEquals(fileName1, resp.getName());

        assertEquals(file1_MimeType, resp.getContent().getMimeType());
        assertEquals("Adobe PDF Document", resp.getContent().getMimeTypeName());

        assertEquals(new Long(file1_originalBytes.length), resp.getContent().getSizeInBytes());
        assertEquals("UTF-8", resp.getContent().getEncoding());

        assertEquals(docModifiedAt.getTime(), resp.getModifiedAt().getTime()); // not changed
        assertEquals(docModifiedBy, resp.getModifiedByUser().getId()); // not changed (ie. not user2)
        assertEquals(UserInfo.getTestDisplayName(docModifiedBy), resp.getModifiedByUser().getDisplayName());

        assertEquals(user2, resp.getSharedByUser().getId());
        assertEquals(UserInfo.getTestDisplayName(user2), resp.getSharedByUser().getDisplayName());

        // -ve test - try to create again (same user) - already exists
        post(URL_SHARED_LINKS, toJsonAsStringNonNull(body), 409);


        // As user 1 ...
        setRequestContext(user1);

        // create shared link to document 2
        body = new HashMap<>();
        body.put("nodeId", d2Id);

        response = post(URL_SHARED_LINKS, toJsonAsStringNonNull(body), 201);
        resp = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), QuickShareLink.class);
        String shared2Id = resp.getId();


        // currently passing auth should make no difference (irrespective of MT vs non-MY enb)

        // access to get shared link info - pass user1 (but ignore in non-MT)
        Map<String, String> params = Collections.singletonMap("include", "allowableOperations");
        response = getSingle(QuickShareLinkEntityResource.class, shared1Id, params, 200);
        resp = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), QuickShareLink.class);

        assertEquals(shared1Id, resp.getId());
        assertEquals(fileName1, resp.getName());
        assertEquals(d1Id, resp.getNodeId());
        assertNull(resp.getAllowableOperations()); // include is ignored

        assertNull(resp.getModifiedByUser().getId()); // userId not returned
        assertEquals(UserInfo.getTestDisplayName(user1), resp.getModifiedByUser().getDisplayName());
        assertNull(resp.getSharedByUser().getId()); // userId not returned
        assertEquals(UserInfo.getTestDisplayName(user2), resp.getSharedByUser().getDisplayName());

        // access to get shared link info - pass user2 (but ignore in non-MT)
        params = Collections.singletonMap("include", "allowableOperations");
        response = getSingle(QuickShareLinkEntityResource.class, shared1Id, params, 200);
        resp = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), QuickShareLink.class);

        assertEquals(shared1Id, resp.getId());
        assertEquals(fileName1, resp.getName());
        assertEquals(d1Id, resp.getNodeId());
        assertNull(resp.getAllowableOperations()); // include is ignored

        assertNull(resp.getModifiedByUser().getId()); // userId not returned
        assertEquals(UserInfo.getTestDisplayName(user1), resp.getModifiedByUser().getDisplayName());
        assertNull(resp.getSharedByUser().getId()); // userId not returned
        assertEquals(UserInfo.getTestDisplayName(user2), resp.getSharedByUser().getDisplayName());


        // allowable operations not included - no params
        response = getSingle(QuickShareLinkEntityResource.class, shared1Id, null, 200);
        resp = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), QuickShareLink.class);
        assertNull(resp.getAllowableOperations());

        setRequestContext(null);

        // unauth access to get shared link info
        params = Collections.singletonMap("include", "allowableOperations"); // note: this will be ignore for unauth access
        response = getSingle(QuickShareLinkEntityResource.class, shared1Id, params, 200);
        resp = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), QuickShareLink.class);

        assertEquals(shared1Id, resp.getId());
        assertEquals(fileName1, resp.getName());
        assertEquals(d1Id, resp.getNodeId());
        assertNull(resp.getAllowableOperations()); // include is ignored

        assertNull(resp.getModifiedByUser().getId()); // userId not returned
        assertEquals(UserInfo.getTestDisplayName(user1), resp.getModifiedByUser().getDisplayName());
        assertNull(resp.getSharedByUser().getId()); // userId not returned
        assertEquals(UserInfo.getTestDisplayName(user2), resp.getSharedByUser().getDisplayName());

        // unauth access to file 1 content (via shared link)
        response = getSingle(QuickShareLinkEntityResource.class, shared1Id + "/content", null, 200);
        assertArrayEquals(file1_originalBytes, response.getResponseAsBytes());
        Map<String, String> responseHeaders = response.getHeaders();
        assertNotNull(responseHeaders);
        assertEquals(file1_MimeType+";charset=UTF-8", responseHeaders.get("Content-Type"));
        assertNotNull(responseHeaders.get("Expires"));
        assertEquals("attachment; filename=\"" + fileName1 + "\"; filename*=UTF-8''" + fileName1 + "", responseHeaders.get("Content-Disposition"));
        String lastModifiedHeader = responseHeaders.get(LAST_MODIFIED_HEADER);
        assertNotNull(lastModifiedHeader);
        // Test 304 response
        Map<String, String> headers = Collections.singletonMap(IF_MODIFIED_SINCE_HEADER, lastModifiedHeader);
        getSingle(URL_SHARED_LINKS, shared1Id + "/content", null, headers, 304);

        // unauth access to file 1 content (via shared link) - without Content-Disposition header (attachment=false)
        params = new HashMap<>();
        params.put("attachment", "false");
        response = getSingle(QuickShareLinkEntityResource.class, shared1Id + "/content", params, 200);
        assertArrayEquals(file1_originalBytes, response.getResponseAsBytes());
        responseHeaders = response.getHeaders();
        assertNotNull(responseHeaders);
        assertEquals(file1_MimeType+";charset=UTF-8", responseHeaders.get("Content-Type"));
        assertNotNull(responseHeaders.get(LAST_MODIFIED_HEADER));
        assertNotNull(responseHeaders.get("Expires"));
        assertNull(responseHeaders.get("Content-Disposition"));


        // unauth access to file 2 content (via shared link)
        response = getSingle(QuickShareLinkEntityResource.class, shared2Id + "/content", null, 200);
        assertArrayEquals(content2Text.getBytes(), response.getResponseAsBytes());
        responseHeaders = response.getHeaders();
        assertNotNull(responseHeaders);
        assertEquals(file2_MimeType+";charset=ISO-8859-1", responseHeaders.get("Content-Type"));
        assertNotNull(responseHeaders.get("Expires"));
        assertNotNull(responseHeaders.get(LAST_MODIFIED_HEADER));
        assertEquals("attachment; filename=\"" + fileName2 + "\"; filename*=UTF-8''" + fileName2 + "", responseHeaders.get("Content-Disposition"));

        // -ve test - unauth access to get shared link file content - without Content-Disposition header (attachment=false) - header ignored (plain text is not in white list)
        params = new HashMap<>();
        params.put("attachment", "false");
        response = getSingle(QuickShareLinkEntityResource.class, shared2Id + "/content", params, 200);
        assertEquals("attachment; filename=\"" + fileName2 + "\"; filename*=UTF-8''" + fileName2 + "", response.getHeaders().get("Content-Disposition"));

        // -ve shared link rendition tests
        {
            // -ve test - try to get non-existent rendition content
            getSingle(QuickShareLinkEntityResource.class, shared1Id + "/renditions/doclib/content", null, 404);

            // -ve test - try to get unregistered rendition content
            getSingle(QuickShareLinkEntityResource.class, shared1Id + "/renditions/dummy/content", null, 404);
        }

        // unauth access to get rendition info for a shared link (available => CREATED rendition only)
        // -ve shared link rendition tests
        {
            // -ve test - try to get not created rendition for the given shared link
            getSingle(QuickShareLinkEntityResource.class, shared1Id + "/renditions/doclib", null, 404);

            // -ve test - try to get unregistered rendition
            getSingle(QuickShareLinkEntityResource.class, shared1Id + "/renditions/dummy", null, 404);
        }

        // unauth access to get shared link renditions info (available => CREATED renditions only)
        response = getAll(URL_SHARED_LINKS + "/" + shared1Id + "/renditions", null, 200);
        List<Rendition> renditions = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Rendition.class);
        assertEquals(0, renditions.size());

        // create rendition of pdf doc - note: for some reason create rendition of txt doc fail on build m/c (TBC) ?
        setRequestContext(user2);
        
        Rendition rendition = createAndGetRendition(d1Id, "doclib");
        assertNotNull(rendition);
        assertEquals(Rendition.RenditionStatus.CREATED, rendition.getStatus());

        setRequestContext(null);


        // unauth access to get shared link renditions info (available => CREATED renditions only)
        response = getAll(URL_SHARED_LINKS + "/" + shared1Id + "/renditions", null, 200);
        renditions = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Rendition.class);
        assertEquals(1, renditions.size());
        assertEquals(Rendition.RenditionStatus.CREATED, renditions.get(0).getStatus());
        assertEquals("doclib", renditions.get(0).getId());

        {
            // try to get a created rendition for the given shared link
            getSingle(QuickShareLinkEntityResource.class, shared1Id + "/renditions/doclib", null, 200);
        }

        // unauth access to get shared link file rendition content
        response = getSingle(QuickShareLinkEntityResource.class, shared1Id + "/renditions/doclib/content", null, 200);
        assertTrue(response.getResponseAsBytes().length > 0);
        responseHeaders = response.getHeaders();
        assertNotNull(responseHeaders);
        assertEquals(MimetypeMap.MIMETYPE_IMAGE_PNG+";charset=UTF-8", responseHeaders.get("Content-Type"));
        assertNotNull(responseHeaders.get(LAST_MODIFIED_HEADER));
        assertNotNull(responseHeaders.get("Expires"));
        String docName = "doclib";
        assertEquals("attachment; filename=\"" + docName + "\"; filename*=UTF-8''" + docName + "", responseHeaders.get("Content-Disposition"));

        // unauth access to get shared link file rendition content - without Content-Disposition header (attachment=false)
        params = new HashMap<>();
        params.put("attachment", "false");
        response = getSingle(QuickShareLinkEntityResource.class, shared1Id + "/renditions/doclib/content", params, 200);
        assertTrue(response.getResponseAsBytes().length > 0);
        responseHeaders = response.getHeaders();
        assertNotNull(responseHeaders);
        assertEquals(MimetypeMap.MIMETYPE_IMAGE_PNG+";charset=UTF-8", responseHeaders.get("Content-Type"));
        assertNotNull(responseHeaders.get("Expires"));
        assertNull(responseHeaders.get("Content-Disposition"));
        lastModifiedHeader = responseHeaders.get(LAST_MODIFIED_HEADER);
        assertNotNull(lastModifiedHeader);
        // Test 304 response
        headers = Collections.singletonMap(IF_MODIFIED_SINCE_HEADER, lastModifiedHeader);
        getSingle(URL_SHARED_LINKS, shared1Id + "/renditions/doclib/content", null, headers, 304);


        // -ve delete tests
        {
            // -ve test - unauthenticated
            setRequestContext(null);
            deleteSharedLink(shared1Id, 401);
            
            setRequestContext(user1);

            // -ve test - user1 cannot delete shared link
            deleteSharedLink(shared1Id, 403);
            
            // -ve test - delete - cannot delete non-existent link
            deleteSharedLink("dummy", 404);
        }


        // -ve create tests
        {
            // As user 1 ...

            // -ve test - try to create again (different user, that has read permission) - already exists
            body = new HashMap<>();
            body.put("nodeId", d1Id);
            post(URL_SHARED_LINKS, toJsonAsStringNonNull(body), 409);

            // -ve - create - missing nodeId
            body = new HashMap<>();
            post(URL_SHARED_LINKS, toJsonAsStringNonNull(body), 400);

            // -ve - create - unknown nodeId
            body = new HashMap<>();
            body.put("nodeId", "dummy");
            post(URL_SHARED_LINKS, toJsonAsStringNonNull(body), 404);

            // -ve - create - try to link to folder (ie. not a file)
            String f1Id = createFolder(myFolderNodeId, "f1 " + RUNID).getId();
            body = new HashMap<>();
            body.put("nodeId", f1Id);
            post(URL_SHARED_LINKS, toJsonAsStringNonNull(body), 400);

            // -ve test - cannot create if user does not have permission to read
            setRequestContext(user2);
            body = new HashMap<>();
            body.put("nodeId", d2Id);
            post(URL_SHARED_LINKS, toJsonAsStringNonNull(body), 403);

            // -ve test - unauthenticated
            setRequestContext(null);
            body = new HashMap<>();
            body.put("nodeId", d1Id);
            post(URL_SHARED_LINKS, toJsonAsStringNonNull(body), 401);
        }


        // delete shared link
        setRequestContext(user2);
        deleteSharedLink(shared1Id);

        // -ve test - delete - cannot delete non-existent link
        setRequestContext(user1);
        deleteSharedLink(shared1Id, 404);

        setRequestContext(user2);

        response = getSingle(NodesEntityResource.class, d1Id, null, 200);
        nodeResp = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), Node.class);

        assertEquals(docModifiedAt.getTime(), nodeResp.getModifiedAt().getTime()); // not changed
        assertEquals(docModifiedBy, nodeResp.getModifiedByUser().getId()); // not changed (ie. not user2)


        // -ve get tests
        {
            // try to get link that has been deleted (see above)
            getSingle(QuickShareLinkEntityResource.class, shared1Id, null, 404);
            getSingle(QuickShareLinkEntityResource.class, shared1Id + "/content", null, 404);

            // try to get non-existent link
            getSingle(QuickShareLinkEntityResource.class, "dummy", null, 404);
            getSingle(QuickShareLinkEntityResource.class, "dummy/content", null, 404);
        }

        // TODO if and when these tests are optionally runnable via remote env then we could skip this part of the test
        // (else need to verify test mechanism for enterprise admin via jmx ... etc)

        QuickShareLinksImpl quickShareLinks = applicationContext.getBean("quickShareLinks", QuickShareLinksImpl.class);
        try
        {
            quickShareLinks.setEnabled(false);

            setRequestContext(user1);

            // -ve - disabled service tests
            body.put("nodeId", "dummy");
            post(URL_SHARED_LINKS, toJsonAsStringNonNull(body), 501);

            setRequestContext(null);
            getSingle(QuickShareLinkEntityResource.class, "dummy", null, 501);
            getSingle(QuickShareLinkEntityResource.class, "dummy/content", null, 501);

            setRequestContext(user1);
            deleteSharedLink("dummy", 501);
        }
        finally
        {
            quickShareLinks.setEnabled(true);
        }
    }

    /**
     * Tests find shared links to file (content)
     *
     * Note: relies on search service
     *
     * <p>GET:</p>
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/shared-links}
     */
    @Test
    public void testSharedLinkFind() throws Exception
    {
        // As user 1 ...
        setRequestContext(user1);
        
        Paging paging = getPaging(0, 100);

        // Get all shared links visible to user 1 (note: for now assumes clean repo)
        HttpResponse response = getAll(URL_SHARED_LINKS, paging, 200);
        List<QuickShareLink> sharedLinks = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), QuickShareLink.class);
        assertEquals(0, sharedLinks.size());
        
        // create doc d1 - in "My" folder
        String myFolderNodeId = getMyNodeId();
        String content1Text = "The quick brown fox jumps over the lazy dog 1.";
        String docName1 = "content" + RUNID + "_1.txt";
        Document doc1 = createTextFile(myFolderNodeId, docName1, content1Text);
        String d1Id = doc1.getId();

        // create doc d2 - in "Shared" folder
        String sharedFolderNodeId = getSharedNodeId();
        String content2Text = "The quick brown fox jumps over the lazy dog 2.";
        String docName2 = "content" + RUNID + "_2.txt";
        Document doc2 = createTextFile(sharedFolderNodeId, docName2, content1Text);
        String d2Id = doc2.getId();

        // create shared link to doc 1
        Map<String, String> body = new HashMap<>();
        body.put("nodeId", d1Id);
        response = post(URL_SHARED_LINKS, toJsonAsStringNonNull(body), 201);
        QuickShareLink resp = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), QuickShareLink.class);
        String shared1Id = resp.getId();

        // As user 2 ...
        setRequestContext(user2);

        // create shared link to doc 2
        body = new HashMap<>();
        body.put("nodeId", d2Id);
        response = post(URL_SHARED_LINKS, toJsonAsStringNonNull(body), 201);
        resp = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), QuickShareLink.class);
        String shared2Id = resp.getId();


        //
        // find links
        //

        setRequestContext(user1);

        response = getAll(URL_SHARED_LINKS, paging, 200);
        sharedLinks = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), QuickShareLink.class);
        assertEquals(2, sharedLinks.size());
        assertEquals(shared2Id, sharedLinks.get(0).getId());
        assertEquals(d2Id, sharedLinks.get(0).getNodeId());
        assertEquals(shared1Id, sharedLinks.get(1).getId());
        assertEquals(d1Id, sharedLinks.get(1).getNodeId());

        setRequestContext(user2);

        response = getAll(URL_SHARED_LINKS, paging, 200);
        sharedLinks = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), QuickShareLink.class);
        assertEquals(1, sharedLinks.size());
        assertEquals(shared2Id, sharedLinks.get(0).getId());
        assertEquals(d2Id, sharedLinks.get(0).getNodeId());

        setRequestContext(user1);

        // find my links
        Map<String, String> params = new HashMap<>();
        params.put("where", "("+ QuickShareLinks.PARAM_SHAREDBY+"='"+People.DEFAULT_USER+"')");

        response = getAll(URL_SHARED_LINKS, paging, params, 200);
        sharedLinks = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), QuickShareLink.class);
        assertEquals(1, sharedLinks.size());
        assertEquals(shared1Id, sharedLinks.get(0).getId());
        assertEquals(d1Id, sharedLinks.get(0).getNodeId());

        // find links shared by a given user
        params = new HashMap<>();
        params.put("where", "("+ QuickShareLinks.PARAM_SHAREDBY+"='"+user2+"')");

        response = getAll(URL_SHARED_LINKS, paging, params, 200);
        sharedLinks = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), QuickShareLink.class);
        assertEquals(1, sharedLinks.size());
        assertEquals(shared2Id, sharedLinks.get(0).getId());
        assertEquals(d2Id, sharedLinks.get(0).getNodeId());

        setRequestContext(null);

        // -ve test - unauthenticated
        getAll(URL_SHARED_LINKS, paging, params, 401);


        // delete the shared links
        setRequestContext(user1);
        deleteSharedLink(shared1Id);

        setRequestContext(user2);
        deleteSharedLink(shared2Id);


        // TODO if and when these tests are optionally runnable via remote env then we could skip this part of the test
        // (else need to verify test mechanism for enterprise admin via jmx ... etc)

        setRequestContext(user1);
        
        QuickShareLinksImpl quickShareLinks = applicationContext.getBean("quickShareLinks", QuickShareLinksImpl.class);
        try
        {
            quickShareLinks.setEnabled(false);

            // -ve - disabled service tests
            getAll(URL_SHARED_LINKS, paging, 501);
        }
        finally
        {
            quickShareLinks.setEnabled(true);
        }
    }

    /**
     * Tests emailing shared links.
     * <p>POST:</p>
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/shared-links/<sharedId>/email}
     */
    @Test
    public void testEmailSharedLink() throws Exception
    {
        setRequestContext(user1);
        
        // Create plain text document
        String myFolderNodeId = getMyNodeId();
        String contentText = "The quick brown fox jumps over the lazy dog.";
        String fileName = "file-" + RUNID + ".txt";
        Document doc = createTextFile(myFolderNodeId, fileName, contentText);
        String docId = doc.getId();

        // Create shared link to document
        Map<String, String> body = Collections.singletonMap("nodeId", docId);
        HttpResponse response = post(URL_SHARED_LINKS, toJsonAsStringNonNull(body), 201);
        QuickShareLink resp = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), QuickShareLink.class);
        String sharedId = resp.getId();
        assertNotNull(sharedId);
        assertEquals(fileName, resp.getName());

        // Email request with minimal properties
        QuickShareLinkEmailRequest request = new QuickShareLinkEmailRequest();
        request.setClient("sfs");
        List<String> recipients = new ArrayList<>(2);
        recipients.add(user2 + "@acme.test");
        recipients.add(user2 + "@ping.test");
        request.setRecipientEmails(recipients);
        post(getEmailSharedLinkUrl(sharedId), RestApiUtil.toJsonAsString(request), 202);

        // Email request with all the properties
        request = new QuickShareLinkEmailRequest();
        request.setClient("sfs");
        request.setMessage("My custom message!");
        request.setLocale(Locale.UK.toString());
        recipients = Collections.singletonList(user2 + "@acme.test");
        request.setRecipientEmails(recipients);
        post(getEmailSharedLinkUrl(sharedId), RestApiUtil.toJsonAsString(request), 202);

        // -ve tests
        // sharedId path parameter does not exist
        post(getEmailSharedLinkUrl(sharedId + System.currentTimeMillis()), RestApiUtil.toJsonAsString(request), 404);

        // Unregistered client
        request = new QuickShareLinkEmailRequest();
        request.setClient("VeryCoolClient" + System.currentTimeMillis());
        List<String> user2Email = Collections.singletonList(user2 + "@acme.test");
        request.setRecipientEmails(user2Email);
        post(getEmailSharedLinkUrl(sharedId), RestApiUtil.toJsonAsString(request), 400);

        // client is mandatory
        request.setClient(null);
        post(getEmailSharedLinkUrl(sharedId), RestApiUtil.toJsonAsString(request), 400);

        // recipientEmails is mandatory
        request.setClient("sfs");
        request.setRecipientEmails(null);
        post(getEmailSharedLinkUrl(sharedId), RestApiUtil.toJsonAsString(request), 400);

        // TODO if and when these tests are optionally runnable via remote env then we could skip this part of the test
        // (else need to verify test mechanism for enterprise admin via jmx ... etc)
        QuickShareLinksImpl quickShareLinks = applicationContext.getBean("quickShareLinks", QuickShareLinksImpl.class);
        try
        {
            quickShareLinks.setEnabled(false);
            request = new QuickShareLinkEmailRequest();
            request.setClient("sfs");
            request.setRecipientEmails(user2Email);
            post(getEmailSharedLinkUrl(sharedId), RestApiUtil.toJsonAsString(request), 501);
        }
        finally
        {
            quickShareLinks.setEnabled(true);
        }
    }

    /**
     * Tests shared links to file (content) in a multi-tenant system.
     *
     * <p>POST:</p>
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/shared-links}
     *
     * <p>DELETE:</p>
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/shared-links/<sharedId>}
     *
     * <p>GET:</p>
     * The following do not require authentication
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/shared-links/<sharedId>}
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/shared-links/<sharedId>/content}
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/shared-links/<sharedId>/renditions}
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/shared-links/<sharedId>/renditions/<renditionId>}
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/shared-links/<sharedId>/renditions/<renditionId>/content}
     *
     */
    // TODO now covered by testSharedLinkCreateGetDelete ? (since base class now uses tenant context by default)
    @Test
    public void testSharedLinkCreateGetDelete_MultiTenant() throws Exception
    {
        // As user1
        setRequestContext(user1);

        String docLibNodeId = getSiteContainerNodeId(tSiteId, "documentLibrary");
        
        String folderName = "folder" + System.currentTimeMillis() + "_1";
        String folderId = createFolder(docLibNodeId, folderName, null).getId();

        // create doc d1 - pdf
        String fileName1 = "quick" + RUNID + "_1.pdf";
        File file1 = getResourceFile("quick.pdf");

        byte[] file1_originalBytes = Files.readAllBytes(Paths.get(file1.getAbsolutePath()));

        String file1_MimeType = MimetypeMap.MIMETYPE_PDF;

        MultiPartBuilder.MultiPartRequest reqBody = MultiPartBuilder.create()
                    .setFileData(new MultiPartBuilder.FileData(fileName1, file1, file1_MimeType))
                    .build();

        HttpResponse response = post(getNodeChildrenUrl(folderId), reqBody.getBody(), null, reqBody.getContentType(), 201);
        Document doc1 = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), Document.class);
        String d1Id = doc1.getId();
        assertNotNull(d1Id);

        // create shared link to document 1
        Map<String, String> body = new HashMap<>();
        body.put("nodeId", d1Id);
        response = post(URL_SHARED_LINKS, toJsonAsStringNonNull(body), 201);
        QuickShareLink resp = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), QuickShareLink.class);
        String shared1Id = resp.getId();
        assertNotNull(shared1Id);
        assertEquals(d1Id, resp.getNodeId());
        assertEquals(fileName1, resp.getName());
        assertEquals(file1_MimeType, resp.getContent().getMimeType());
        assertEquals(user1, resp.getSharedByUser().getId());

        // allowable operations not included - no params
        response = getSingle(QuickShareLinkEntityResource.class, shared1Id, null, 200);
        resp = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), QuickShareLink.class);
        assertNull(resp.getAllowableOperations());

        setRequestContext(null);

        // unauth access to get shared link info
        Map<String, String> params = Collections.singletonMap("include", "allowableOperations"); // note: this will be ignore for unauth access
        response = getSingle(QuickShareLinkEntityResource.class, shared1Id, params, 200);
        resp = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), QuickShareLink.class);
        assertEquals(shared1Id, resp.getId());
        assertEquals(fileName1, resp.getName());
        assertEquals(d1Id, resp.getNodeId());
        assertNull(resp.getAllowableOperations()); // include is ignored

        // unauth access to file 1 content (via shared link)
        response = getSingle(QuickShareLinkEntityResource.class, shared1Id + "/content", null, 200);
        assertArrayEquals(file1_originalBytes, response.getResponseAsBytes());
        Map<String, String> responseHeaders = response.getHeaders();
        assertNotNull(responseHeaders);
        assertEquals(file1_MimeType + ";charset=UTF-8", responseHeaders.get("Content-Type"));
        assertNotNull(responseHeaders.get("Expires"));
        assertEquals("attachment; filename=\"" + fileName1 + "\"; filename*=UTF-8''" + fileName1 + "", responseHeaders.get("Content-Disposition"));
        String lastModifiedHeader = responseHeaders.get(LAST_MODIFIED_HEADER);
        assertNotNull(lastModifiedHeader);
        // Test 304 response
        Map<String, String> headers = Collections.singletonMap(IF_MODIFIED_SINCE_HEADER, lastModifiedHeader);
        getSingle(URL_SHARED_LINKS, shared1Id + "/content", null, headers, 304);

        // unauth access to file 1 content (via shared link) - without Content-Disposition header (attachment=false)
        params = new HashMap<>();
        params.put("attachment", "false");
        response = getSingle(QuickShareLinkEntityResource.class, shared1Id + "/content", params, 200);
        assertArrayEquals(file1_originalBytes, response.getResponseAsBytes());
        responseHeaders = response.getHeaders();
        assertNotNull(responseHeaders);
        assertEquals(file1_MimeType + ";charset=UTF-8", responseHeaders.get("Content-Type"));
        assertNotNull(responseHeaders.get(LAST_MODIFIED_HEADER));
        assertNotNull(responseHeaders.get("Expires"));
        assertNull(responseHeaders.get("Content-Disposition"));

        // -ve shared link rendition tests
        {
            // -ve test - try to get non-existent rendition content
            getSingle(QuickShareLinkEntityResource.class, shared1Id + "/renditions/doclib/content", null, 404);

            // -ve test - try to get unregistered rendition content
            getSingle(QuickShareLinkEntityResource.class, shared1Id + "/renditions/dummy/content", null, 404);
        }

        // unauth access to get rendition info for a shared link (available => CREATED rendition only)
        // -ve shared link rendition tests
        {
            // -ve test - try to get not created rendition for the given shared link
            getSingle(QuickShareLinkEntityResource.class, shared1Id + "/renditions/doclib", null, 404);

            // -ve test - try to get unregistered rendition
            getSingle(QuickShareLinkEntityResource.class, shared1Id + "/renditions/dummy", null, 404);
        }

        // unauth access to get shared link renditions info (available => CREATED renditions only)
        response = getAll(URL_SHARED_LINKS + "/" + shared1Id + "/renditions", null, 200);
        List<Rendition> renditions = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Rendition.class);
        assertEquals(0, renditions.size());

        // create rendition of pdf doc - note: for some reason create rendition of txt doc fail on build m/c (TBC) ?
        setRequestContext(user1);

        Rendition rendition = createAndGetRendition(d1Id, "doclib");
        assertNotNull(rendition);
        assertEquals(Rendition.RenditionStatus.CREATED, rendition.getStatus());

        setRequestContext(null);

        // unauth access to get shared link renditions info (available => CREATED renditions only)
        response = getAll(URL_SHARED_LINKS + "/" + shared1Id + "/renditions", null, 200);
        renditions = RestApiUtil.parseRestApiEntries(response.getJsonResponse(), Rendition.class);
        assertEquals(1, renditions.size());
        assertEquals(Rendition.RenditionStatus.CREATED, renditions.get(0).getStatus());
        assertEquals("doclib", renditions.get(0).getId());

        // unauth access to get rendition info for a shared link (available => CREATED rendition only)
        {
            // get a created rendition for the given shared link
            getSingle(QuickShareLinkEntityResource.class, shared1Id + "/renditions/doclib", null, 200);
        }

        // unauth access to get shared link file rendition content
        response = getSingle(QuickShareLinkEntityResource.class, shared1Id + "/renditions/doclib/content", null, 200);
        assertTrue(response.getResponseAsBytes().length > 0);
        responseHeaders = response.getHeaders();
        assertNotNull(responseHeaders);
        assertEquals(MimetypeMap.MIMETYPE_IMAGE_PNG + ";charset=UTF-8", responseHeaders.get("Content-Type"));
        assertNotNull(responseHeaders.get(LAST_MODIFIED_HEADER));
        assertNotNull(responseHeaders.get("Expires"));
        String docName = "doclib";
        assertEquals("attachment; filename=\"" + docName + "\"; filename*=UTF-8''" + docName + "", responseHeaders.get("Content-Disposition"));

        // unauth access to get shared link file rendition content - without Content-Disposition header (attachment=false)
        params = new HashMap<>();
        params.put("attachment", "false");
        response = getSingle(QuickShareLinkEntityResource.class, shared1Id + "/renditions/doclib/content", params, 200);
        assertTrue(response.getResponseAsBytes().length > 0);
        responseHeaders = response.getHeaders();
        assertNotNull(responseHeaders);
        assertEquals(MimetypeMap.MIMETYPE_IMAGE_PNG + ";charset=UTF-8", responseHeaders.get("Content-Type"));
        assertNotNull(responseHeaders.get("Expires"));
        assertNull(responseHeaders.get("Content-Disposition"));
        lastModifiedHeader = responseHeaders.get(LAST_MODIFIED_HEADER);
        assertNotNull(lastModifiedHeader);
        // Test 304 response
        headers = Collections.singletonMap(IF_MODIFIED_SINCE_HEADER, lastModifiedHeader);
        getSingle(URL_SHARED_LINKS, shared1Id + "/renditions/doclib/content", null, headers, 304);
        
        // -ve test - userTwoN1 cannot delete shared link
        setRequestContext(user2);
        deleteSharedLink(shared1Id, 403);

        // -ve test - unauthenticated
        setRequestContext(null);
        deleteSharedLink(shared1Id, 401);

        // delete shared link
        setRequestContext(user1);
        deleteSharedLink(shared1Id);
    }

    /**
     * Tests shared links to file with expiry date.
     * <p>POST:</p>
     * {@literal <host>:<port>/alfresco/api/<networkId>/public/alfresco/versions/1/shared-links}
     */
    @Test
    public void testSharedLinkWithExpiryDate() throws Exception
    {
        // Clear any hanging security context from other tests.
        // We add it here as getSchedules method will throw an exception.
        AuthenticationUtil.clearCurrentSecurityContext();
        final int numOfSchedules = getSchedules();
        setRequestContext(user1);

        // Create plain text document
        String myFolderNodeId = getMyNodeId();
        String contentText = "The quick brown fox jumps over the lazy dog.";
        String fileName = "file-" + RUNID + ".txt";
        String docId = createTextFile(myFolderNodeId, fileName, contentText).getId();

        // Create shared link to document
        QuickShareLink body = new QuickShareLink();
        body.setNodeId(docId);
        // Invalid time - passed time
        body.setExpiresAt(DateTime.now().minusSeconds(20).toDate());
        post(URL_SHARED_LINKS, RestApiUtil.toJsonAsString(body), 400);

        // The default expiryDate period is DAYS (see: 'system.quickshare.expiry_date.enforce.minimum.period' property),
        // so the expiry date must be at least 1 day from now
        body.setExpiresAt(DateTime.now().plusMinutes(5).toDate());
        post(URL_SHARED_LINKS, RestApiUtil.toJsonAsString(body), 400);

        // Set the expiry date to be in the next 2 days
        Date time = DateTime.now().plusDays(2).toDate();
        body.setExpiresAt(time);
        // Post the share request
        HttpResponse response = post(URL_SHARED_LINKS, RestApiUtil.toJsonAsString(body), 201);
        QuickShareLink resp = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), QuickShareLink.class);
        assertNotNull(resp.getId());
        assertEquals(fileName, resp.getName());
        assertEquals(time, resp.getExpiresAt());
        // Check that the schedule is persisted
        // Note: No need to check for expiry actions here, as the scheduledPersistedActionService
        // checks that the expiry action is persisted first and if it wasn't will throw an exception.
        assertEquals(numOfSchedules + 1, getSchedules());
        // Delete the shared link
        deleteSharedLink(resp.getId());
        // Check the shred link has been deleted
        getSingle(QuickShareLinkEntityResource.class, resp.getId(), null, 404);
        // As we deleted the shared link, the expiry action and its related schedule should have been removed as well.
        // Check that the schedule is deleted
        assertEquals(numOfSchedules, getSchedules());

        // Set the expiry date to be in the next 24 hours
        time = DateTime.now().plusDays(1).toDate();
        body.setExpiresAt(time);
        // Post the share request
        response = post(URL_SHARED_LINKS, RestApiUtil.toJsonAsString(body), 201);
        resp = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), QuickShareLink.class);
        assertNotNull(resp.getId());
        // Check that the schedule is persisted
        assertEquals(numOfSchedules + 1, getSchedules());
        // Get the shared link info
        response = getSingle(QuickShareLinkEntityResource.class, resp.getId(), null, 200);
        resp = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), QuickShareLink.class);
        assertEquals(fileName, resp.getName());
        assertEquals(time, resp.getExpiresAt());
        // Change the expiry time to be in the next 6 seconds.
        // Here we'll bypass the QuickShareService in order to force the new time.
        // As the QuickShareService by default will enforce the expiry date to not be less than 24 hours.
        forceNewExpiryTime(resp.getId(), DateTime.now().plusSeconds(6).toDate());
        // Wait for 10 seconds - the expiry action should be triggered in the next 6 seconds.
        Thread.sleep((10000));
        // Check that the expiry action unshared the link
        getSingle(QuickShareLinkEntityResource.class, resp.getId(), null, 404);
        // The expiry action and its related schedule should have been removed after the link unshared by the action executor.
        // Check that the schedule is deleted
        assertEquals(numOfSchedules, getSchedules());

        // Create a shared link without an expiry date
        body.setExpiresAt(null);
        response = post(URL_SHARED_LINKS, RestApiUtil.toJsonAsString(body), 201);
        resp = RestApiUtil.parseRestApiEntry(response.getJsonResponse(), QuickShareLink.class);
        assertNotNull(resp.getId());
        assertNull("The 'expiryDate' property should have benn null.", resp.getExpiresAt());
        assertEquals(numOfSchedules, getSchedules());

        // Delete the share link that hasn't got an expiry date
        deleteSharedLink(resp.getId());
    }

    @Override
    public String getScope()
    {
        return "public";
    }

    private String getEmailSharedLinkUrl(String sharedId)
    {
        return URL_SHARED_LINKS + '/' + sharedId + "/email";
    }

    private void deleteSharedLink(String sharedId) throws Exception
    {
        deleteSharedLink(sharedId, 204);
    }

    private void deleteSharedLink(String sharedId, int expectedStatus) throws Exception
    {
        delete(URL_SHARED_LINKS, sharedId, expectedStatus);
    }

    private void forceNewExpiryTime(String sharedId, Date date)
    {
        TenantUtil.runAsSystemTenant(() -> {
            // Load the expiry action and attach the schedule
            QuickShareLinkExpiryAction linkExpiryAction = quickShareLinkExpiryActionPersister
                        .loadQuickShareLinkExpiryAction(QuickShareLinkExpiryActionImpl.createQName(sharedId));
            linkExpiryAction.setSchedule(scheduledPersistedActionService.getSchedule(linkExpiryAction));
            linkExpiryAction.setScheduleStart(date);

            // save the expiry action and the schedule
            transactionHelper.doInTransaction(() -> {
                quickShareLinkExpiryActionPersister.saveQuickShareLinkExpiryAction(linkExpiryAction);
                scheduledPersistedActionService.saveSchedule(linkExpiryAction.getSchedule());
                return null;
            });

            return null;
        }, TenantUtil.getCurrentDomain());
    }

    private int getSchedules()
    {
        return TenantUtil.runAsSystemTenant(() -> scheduledPersistedActionService.listSchedules().size(), TenantUtil.getCurrentDomain());
    }
}
