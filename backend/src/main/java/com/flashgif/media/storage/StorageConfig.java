package com.flashgif.media.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.List;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
@RequiredArgsConstructor
@Slf4j
class StorageConfig {

    private final StorageProperties props;

    @Bean
    S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.pathStyle())
                        .build())
                .build();
    }

    @Bean
    S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.pathStyle())
                        .build())
                .build();
    }

    /** Idempotently create the bucket + permissive dev CORS on startup. */
    @Bean
    BucketBootstrapper bucketBootstrapper(S3Client s3) {
        return new BucketBootstrapper(s3, props);
    }

    @Slf4j
    @RequiredArgsConstructor
    static class BucketBootstrapper {
        private final S3Client s3;
        private final StorageProperties props;

        @jakarta.annotation.PostConstruct
        void run() {
            try {
                s3.headBucket(HeadBucketRequest.builder().bucket(props.bucket()).build());
                log.info("S3 bucket '{}' already exists", props.bucket());
            } catch (NoSuchBucketException e) {
                s3.createBucket(CreateBucketRequest.builder().bucket(props.bucket()).build());
                log.info("Created S3 bucket '{}'", props.bucket());
            }
            try {
                s3.putBucketCors(PutBucketCorsRequest.builder()
                        .bucket(props.bucket())
                        .corsConfiguration(CORSConfiguration.builder()
                                .corsRules(CORSRule.builder()
                                        .allowedMethods("PUT", "GET", "HEAD")
                                        .allowedOrigins("*")                 // dev only; tighten per env
                                        .allowedHeaders("*")
                                        .exposeHeaders("ETag")
                                        .maxAgeSeconds(3600)
                                        .build())
                                .build())
                        .build());
                log.info("Applied dev CORS policy to bucket '{}'", props.bucket());
            } catch (S3Exception ex) {
                log.warn("Could not set CORS on bucket '{}': {}", props.bucket(), ex.getMessage());
            }

            // Renditions must be browser-readable without auth (CDN-style).
            // Originals stay private. Production should front S3 with a CDN
            // and apply the same prefix policy there.
            try {
                String policy = """
                        {
                          "Version": "2012-10-17",
                          "Statement": [{
                            "Effect": "Allow",
                            "Principal": {"AWS": ["*"]},
                            "Action": ["s3:GetObject"],
                            "Resource": ["arn:aws:s3:::%s/renditions/*"]
                          }]
                        }
                        """.formatted(props.bucket());
                s3.putBucketPolicy(PutBucketPolicyRequest.builder()
                        .bucket(props.bucket())
                        .policy(policy)
                        .build());
                log.info("Applied public-read policy for renditions/* on '{}'", props.bucket());
            } catch (S3Exception ex) {
                log.warn("Could not set bucket policy on '{}': {}", props.bucket(), ex.getMessage());
            }
        }
    }
}
