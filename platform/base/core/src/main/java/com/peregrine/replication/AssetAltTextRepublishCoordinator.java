package com.peregrine.replication;

/** Coordinates editor-initiated republishing with asset change observation. */
public interface AssetAltTextRepublishCoordinator {
    void expectEditorSave(String assetPath, String reportToken, String altText);

    /** Persists a save without scheduling publication until the editor chooses an action. */
    void expectDeferredEditorSave(String assetPath, String reportToken, String altText);

    /** Starts publication of a previously deferred save, including after a server restart. */
    boolean republishDeferredEditorSave(String assetPath, String reportToken);

    /** Removes a deferred save from the actionable queue. */
    boolean discardDeferredEditorSave(String assetPath, String reportToken);

    /** Queues the job once and allows retry if queueing fails. */
    boolean queueEditorSave(String assetPath, String reportToken);

    /** Removes this save's marker and reports whether an observation was suppressed. */
    boolean cancelEditorSave(String assetPath, String reportToken);

    /** Aborts an uncommitted save and removes its incomplete report marker. */
    void abortEditorSave(String assetPath, String reportToken);
}
