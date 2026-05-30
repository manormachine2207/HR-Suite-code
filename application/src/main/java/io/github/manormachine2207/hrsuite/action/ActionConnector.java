package io.github.manormachine2207.hrsuite.action;

/** Swappable SPI for executing a workflow action against an external runtime (n8n today). */
public interface ActionConnector {
    ActionResult execute(ActionRequest request);
}
