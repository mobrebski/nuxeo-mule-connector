package org.nuxeo.mule.blob;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.jaxrs.impl.HttpAutomationClient;
import org.nuxeo.ecm.automation.client.model.Blob;
import org.nuxeo.ecm.automation.client.model.FileBlob;
import org.nuxeo.ecm.automation.client.model.PropertyMap;
import org.nuxeo.ecm.automation.client.model.StreamBlob;
import org.nuxeo.ecm.automation.client.rest.api.RestRequest;
import org.nuxeo.ecm.automation.client.rest.api.RestResponse;

public class BlobConverters {

    private static final Logger logger = Logger.getLogger(BlobConverters.class);

    protected static File buildFileFromStream(InputStream stream) throws IOException {
        File tmp = File.createTempFile("nuxeo", ".mule");
        Files.copy(stream, tmp.toPath());
        tmp.deleteOnExit();
        return tmp;
    }

    public static NuxeoBlob fileToBlob(Object input) {
        logger.info("Converting " + input.getClass().getName() + " to Nuxeo Blob");
        if (input instanceof File) {
            logger.info("Creating FileBlob");
            return new NuxeoFileBlob(new FileBlob((File)input));
        } else if (input instanceof FileInputStream) {
            logger.info("Creating StreamBlob");
            FileInputStream stream = (FileInputStream) input;
            if (input.getClass().getSimpleName().contains("ReceiverFileInputStream")) {
                // hack because this f**cking class is private !
                try {
                    Method hiddentGetter = input.getClass().getDeclaredMethod("getCurrentFile", null);
                    hiddentGetter.setAccessible(true);
                    File targetFile = (File) hiddentGetter.invoke(input);
                    if (targetFile==null) {
                        logger.info("target File is null");
                        return new NuxeoFileBlob(new FileBlob(buildFileFromStream(stream)));
                    } else {
                        logger.info("found target File via reflection ");
                        return new NuxeoFileBlob(new FileBlob(targetFile));
                    }
                } catch (NoSuchMethodException e) {
                    logger.error("Can not find getCurrentFile method", e);
                } catch (SecurityException e) {
                    logger.error("Can not access getCurrentFile method", e);
                } catch (Exception e) {
                    logger.error("Can not execute getCurrentFile method", e);
                }
            }
            return new NuxeoBlob(new StreamBlob(stream,"mule.blob", "application/octet-stream"));
        } else if (input instanceof byte[]) {
            logger.info("Creating ByteArray Blob");
            ByteArrayInputStream stream = new ByteArrayInputStream((byte[]) input);
            return new NuxeoBlob(new StreamBlob(stream,"mule.blob", "application/octet-stream"));
        }
        return null;
    }

    protected static String buildDownloadUrl(Map<String,Object> map) throws UnsupportedEncodingException {
        Set<String> keys = map.keySet();
        if (keys.contains("name") && keys.contains("data") && keys.contains("mime-type")) {
            return (String) map.get("data");
        } else {
            return null;
        }
    }


    public static InputStream blobToStream(Session session, Object input) throws UnsupportedEncodingException {

        // simple case : this is a Nuxeo Blob
        if (input instanceof Blob) {
            try {
                return ((Blob)input).getStream();
            } catch (IOException e) {
                logger.error("Unable to get Stream from Nuxeo Blob", e);
                return null;
            }
        }

        String downloadUrl = null;

        // Blob property in a Document
        if (input instanceof PropertyMap) {
            PropertyMap pmap = (PropertyMap)input;
            Map<String,Object> map = pmap.map();
            downloadUrl = buildDownloadUrl(map);
        } else if (input instanceof Map<?, ?>) {
            Map<String,Object> map = (Map<String,Object>)input;
            downloadUrl = buildDownloadUrl(map);
        }

        if (downloadUrl!=null) {
            DownloadClient dc = new DownloadClient((HttpAutomationClient)session.getClient());
            return dc.download(downloadUrl);
        }
        return null;
    }

    public static Blob mapToBlob(Session session, Object input) throws UnsupportedEncodingException {

        String downloadUrl = null;
        Map<String,Object> map = null;
        // Blob property in a Document
        if (input instanceof PropertyMap) {
            PropertyMap pmap = (PropertyMap)input;
            map = pmap.map();
            downloadUrl = buildDownloadUrl(map);
        } else if (input instanceof Map<?, ?>) {
            map = (Map<String,Object>)input;
            downloadUrl = buildDownloadUrl(map);
        }

        if (downloadUrl!=null) {
            DownloadClient dc = new DownloadClient((HttpAutomationClient)session.getClient());
            return new StreamBlob(dc.download(downloadUrl), (String)map.get("name"), (String)map.get("mime-type"));
        }
        return null;
    }


}
