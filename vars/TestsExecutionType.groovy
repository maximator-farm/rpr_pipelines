public class TestsExecutionType {
    public static enum TestsExecutionTypeValues {

        //take only one node for test groups on each GPU
        TAKE_ONE_NODE_PER_GPU,
        //number of parallel threads is equal to number of free nodes
        //each finished thread creates number of new threads which is equal to number of free nodes (if there isn't free nodes - only one thread will be added in queue)
        TAKE_ALL_FREE_NODES,
        //number of parallel threads is equal to number of existent online nodes in Jenkins before run tests
        TAKE_ALL_ONLINE_NODES

    }

    def static valueOf(String name) {
        switch(name) {
            case 'TakeOneNodePerGPU':
                return TestsExecutionTypeValues.TAKE_ONE_NODE_PER_GPU
            case 'TakeAllFreeNodes':
                return TestsExecutionTypeValues.TAKE_ALL_FREE_NODES
            case 'TakeAllOnlineNodes':
                return TestsExecutionTypeValues.TAKE_ALL_ONLINE_NODES
            default:
                return null
        }
    }

    def static TAKE_ONE_NODE_PER_GPU = TestsExecutionTypeValues.TAKE_ONE_NODE_PER_GPU
    def static TAKE_ALL_FREE_NODES = TestsExecutionTypeValues.TAKE_ALL_FREE_NODES
    def static TAKE_ALL_ONLINE_NODES = TestsExecutionTypeValues.TAKE_ALL_ONLINE_NODES
}
