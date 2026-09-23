package io.github.thrhead.taplog.data.persistence

import io.github.thrhead.taplog.core.engine.AtomicCommitBoundary

/**
 * Application-facing access to the local aggregate used by management flows.
 *
 * The inherited Boolean commit contract is intentional: commit rejection remains a storage
 * failure when interpreted by the core event engine.
 */
interface ManagementPersistencePort : AtomicCommitBoundary
