package jmx2gatling.inventory;

/** The role a test element plays in a JMeter plan, which decides its scoping rules. */
public enum ElementKind {
    TEST_PLAN, THREAD_GROUP, SAMPLER, CONTROLLER, CONFIG, PRE_PROCESSOR, POST_PROCESSOR, ASSERTION, TIMER, LISTENER
}
