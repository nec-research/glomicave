package eu.glomicave.persistence;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import eu.glomicave.config.AmazonS3Config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;


public class AmazonS3 {
	private static Logger logger = LogManager.getLogger(AmazonS3.class);

	public static ResponseInputStream<GetObjectResponse> readFileUsingS3Client(String objectKey) {
		String bucketName = AmazonS3Config.getInstance().getBucketName();
		try {
			S3Client s3 = AmazonS3Config.getInstance().getS3Client();				  
			GetObjectRequest getObjectRequest = GetObjectRequest.builder()
			     .bucket(bucketName)
			     .key(objectKey)
			     .build();
			  		
			ResponseInputStream<GetObjectResponse> responseInputStream = s3.getObject(getObjectRequest);
			return responseInputStream;
			  
		} catch (S3Exception e) {
			System.err.println(e.awsErrorDetails().errorMessage());			  
			return null;
		}
	}
	
	/** Get all directories in S3 folder pointed by dataPrefix.	*/
	public static List<CommonPrefix> getDirList(String dataPrefix) {
		String bucketName = AmazonS3Config.getInstance().getBucketName();			
		try {
			S3Client s3 = AmazonS3Config.getInstance().getS3Client();
			ListObjectsRequest request = ListObjectsRequest.builder()
				.bucket(bucketName)
				.prefix(dataPrefix)
				.delimiter("/")
				.build();
			
			ListObjectsResponse response = s3.listObjects(request);	
			List<CommonPrefix> pathList = response.commonPrefixes();
			return pathList;
	
		} catch (S3Exception e) {
			  System.err.println(e.awsErrorDetails().errorMessage());		
			  logger.error("Error while listing s3 objects", e);
			  return null;
		}
	}
	
	/** Get list of objects in S3 folder pointed by dataPrefix. */
	public static List<S3Object> getObjectList(String dataPrefix) {
		String bucketName = AmazonS3Config.getInstance().getBucketName();			
		try {
			S3Client s3 = AmazonS3Config.getInstance().getS3Client();		
			ListObjectsRequest request = ListObjectsRequest.builder()
				.bucket(bucketName)
				.prefix(dataPrefix)
				.delimiter("/")
				.build();
		
			ListObjectsResponse response = s3.listObjects(request);
			List<S3Object> fileList = response.contents();
			return fileList;
		
		} catch (S3Exception e) {
			  System.err.println(e.awsErrorDetails().errorMessage());		
			  logger.error("Error while listing s3 objects", e);
			  return null;
		}	
	}
	
	/** Get list of all objects in S3 folder recursively pointed by dataPrefix. */
	public static List<S3Object> getObjectListRecursively(String dataPrefix) {
		String bucketName = AmazonS3Config.getInstance().getBucketName();			
		try {
			S3Client s3 = AmazonS3Config.getInstance().getS3Client();		
			ListObjectsRequest request = ListObjectsRequest.builder()
				.bucket(bucketName)
				.prefix(dataPrefix)
				.build();
		
			ListObjectsResponse response = s3.listObjects(request);
			List<S3Object> fileList = response.contents();
			return fileList;
		
		} catch (S3Exception e) {
			  System.err.println(e.awsErrorDetails().errorMessage());		
			  logger.error("Error while listing s3 objects", e);
			  return null;
		}	
	}
	
	/** Write local file localFilePath into S3 bucket under objectKey.	*/
	public static String putS3Object(String localFilePath, String objectKey) {
		String bucketName = AmazonS3Config.getInstance().getBucketName();		  
		try {
			S3Client s3 = AmazonS3Config.getInstance().getS3Client();				
		  
			PutObjectRequest putOb = PutObjectRequest.builder()
			    .bucket(bucketName)
			    .key(objectKey)
			    .build();

            PutObjectResponse response = s3.putObject(putOb, RequestBody.fromBytes(getObjectFile(localFilePath)));
            return response.eTag();

        } catch (S3Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }

        return "";
    }
	
	/** Write byte array into S3 bucket under objectKey.	*/
	public static String putS3ObjectFromBytes(byte[] byteBuffer, String objectKey) {
		String bucketName = AmazonS3Config.getInstance().getBucketName();		  
		try {
			S3Client s3 = AmazonS3Config.getInstance().getS3Client();				
		  
			PutObjectRequest putOb = PutObjectRequest.builder()
			    .bucket(bucketName)
			    .key(objectKey)
			    .build();

            PutObjectResponse response = s3.putObject(putOb, RequestBody.fromBytes(byteBuffer));
            return response.eTag();

        } catch (S3Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }

        return "";
    }
	
	/** Delete objects listed in objectNames */
    public static void deleteBucketObjects(List<String> objectNames) {
        ArrayList<ObjectIdentifier> toDelete = new ArrayList<>();
        for (String objectName : objectNames) { 
	        toDelete.add(ObjectIdentifier.builder()
	            .key(objectName)
	            .build());
        }
        
        try {
			S3Client s3 = AmazonS3Config.getInstance().getS3Client();		
        	
            DeleteObjectsRequest dor = DeleteObjectsRequest.builder()
                .bucket(AmazonS3Config.getInstance().getBucketName())
                .delete(Delete.builder()
                .objects(toDelete).build())
                .build();
            
            s3.deleteObjects(dor);

        } catch (S3Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
			logger.error("Error deleting s3 objects", e);
            //System.exit(1);
        }
    }
    
	/** Delete objects recursively from S3 folder pointed by dataPrefix. */
    public static void deleteObjectsInDir(String dataPrefix) {
		List<String> objectsToDelete = new ArrayList<>();
		List<S3Object> paths = AmazonS3.getObjectListRecursively(dataPrefix);
		
		for (S3Object objectPath : paths) {				
			logger.info("Object " + objectPath.key() + " will be deleted.");
			objectsToDelete.add(objectPath.key());
		}	
		if (objectsToDelete.size() > 0) {
			AmazonS3.deleteBucketObjects(objectsToDelete);
		}
    }
    
	/** Helper method to read data from local file before moving to S3.	*/
	private static byte[] getObjectFile(String filePath) {
        FileInputStream fileInputStream = null;
        byte[] bytesArray = null;

        try {
            File file = new File(filePath);
            fileInputStream = new FileInputStream(file);
            bytesArray = new byte[(int) file.length()];
            fileInputStream.read(bytesArray);
            fileInputStream.close();
            
            file.delete();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return bytesArray;
    }
}	





	/*
	public boolean initializeUpload() {

        InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(destBucketName, filename);
        initRequest.setObjectMetadata(getObjectMetadata()); // if we want to set object metadata in S3 bucket
        initRequest.setTagging(getObjectTagging()); // if we want to set object tags in S3 bucket

        uploadId = s3Client.initiateMultipartUpload(initRequest).getUploadId();

        return false;
    }
	
	
	public void uploadPartAsync(ByteArrayInputStream inputStream) {
        submitTaskForUploading(inputStream, false);
    }
	
	public void uploadFinalPartAsync(ByteArrayInputStream inputStream) {
        try {
            submitTaskForUploading(inputStream, true);

            // wait and get all PartETags from ExecutorService and submit it in CompleteMultipartUploadRequest
            List<PartETag> partETags = new ArrayList<>();
            for (Future<PartETag> partETagFuture : futuresPartETags) {
                partETags.add(partETagFuture.get());
            }

            // Complete the multipart upload
            CompleteMultipartUploadRequest completeRequest = new CompleteMultipartUploadRequest(destBucketName, filename, uploadId, partETags);
            s3Client.completeMultipartUpload(completeRequest);

        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            // finally close the executor service
            this.shutdownAndAwaitTermination();
        }

    }
	
	private void shutdownAndAwaitTermination() {
        log.debug("executor service await and shutdown");
        this.executorService.shutdown();
        try {
            this.executorService.awaitTermination(AWAIT_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.debug("Interrupted while awaiting ThreadPoolExecutor to shutdown");
        }
        this.executorService.shutdownNow();
    }

	
	
	private void submitTaskForUploading(ByteArrayInputStream inputStream, boolean isFinalPart) {
        if (uploadId == null || uploadId.isEmpty()) {
            throw new IllegalStateException("Initial Multipart Upload Request has not been set.");
        }

        if (destBucketName == null || destBucketName.isEmpty()) {
            throw new IllegalStateException("Destination bucket has not been set.");
        }

        if (filename == null || filename.isEmpty()) {
            throw new IllegalStateException("Uploading file name has not been set.");
        }

        submitTaskToExecutorService(() -> {
            int eachPartId = uploadPartId.incrementAndGet();
            UploadPartRequest uploadRequest = new UploadPartRequest()
                    .withBucketName(destBucketName)
                    .withKey(filename)
                    .withUploadId(uploadId)
                    .withPartNumber(eachPartId) // partNumber should be between 1 and 10000 inclusively
                    .withPartSize(inputStream.available())
                    .withInputStream(inputStream);

            if (isFinalPart) {
                uploadRequest.withLastPart(true);
            }

            log.info(String.format("Submitting uploadPartId: %d of partSize: %d", eachPartId, inputStream.available()));

            UploadPartResult uploadResult = s3Client.uploadPart(uploadRequest);

            log.info(String.format("Successfully submitted uploadPartId: %d", eachPartId));
            return uploadResult.getPartETag();
        });
    }

    private void submitTaskToExecutorService(Callable<PartETag> callable) {
        // we are submitting each part in executor service and it does not matter which part gets upload first
        // because in each part we have assigned PartNumber from "uploadPartId.incrementAndGet()"
        // and S3 will accumulate file by using PartNumber order after CompleteMultipartUploadRequest
        Future<PartETag> partETagFuture = this.executorService.submit(callable);
        this.futuresPartETags.add(partETagFuture);
    }

    private ObjectTagging getObjectTagging() {
        // create tags list for uploading file
        return new ObjectTagging(new ArrayList<>());
    }

    private ObjectMetadata getObjectMetadata() {
        // create metadata for uploading file
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType("application/zip");
        return objectMetadata;
    }

	
    
    
    final int UPLOAD_PART_SIZE = 10 * Constants.MB; // Part Size should not be less than 5 MB while using MultipartUpload
    final String destBucketName = "_destination_bucket_name_";
    final String filename = "_filename_";

    AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1).build();
    S3MultipartUpload multipartUpload = new S3MultipartUpload(destBucketName, filename, s3Client);
    multipartUpload.initializeUpload();

    URL url = null;
    HttpURLConnection connection = null;

    try {

        url = new URL("_remote_url_of_uploading_file_");

        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        InputStream inputStream = connection.getInputStream();

        int bytesRead, bytesAdded = 0;
        byte[] data = new byte[UPLOAD_PART_SIZE];
        ByteArrayOutputStream bufferOutputStream = new ByteArrayOutputStream();

        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {

            bufferOutputStream.write(data, 0, bytesRead);

            if (bytesAdded < UPLOAD_PART_SIZE) {
                // continue writing to same output stream unless it's size gets more than UPLOAD_PART_SIZE
                bytesAdded += bytesRead;
                continue;
            }
            multipartUpload.uploadPartAsync(new ByteArrayInputStream(bufferOutputStream.toByteArray()));
            bufferOutputStream.reset(); // flush the bufferOutputStream
            bytesAdded = 0; // reset the bytes added to 0
        }

        // upload remaining part of output stream as final part
        // bufferOutputStream size can be less than 5 MB as it is the last part of upload
        multipartUpload.uploadFinalPartAsync(new ByteArrayInputStream(bufferOutputStream.toByteArray()));
    } catch (Exception e) {
        e.printStackTrace();
    } finally {
        if (connection != null) {
            connection.disconnect();
        }
    }
}
    
	*/
	
	


