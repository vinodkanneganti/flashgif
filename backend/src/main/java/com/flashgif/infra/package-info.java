/**
 * Infrastructure: cross-cutting configuration shared across feature modules —
 * security filter chains, OpenAPI/Swagger, persistence config, messaging config,
 * S3 client, Redis/Bucket4j rate limiting, common exception handlers.
 *
 * <p>Feature modules may depend on {@code infra}; {@code infra} must not
 * depend on any feature module.
 */
package com.flashgif.infra;
