public class ExpectedExecutionThrowType {
    public static enum ExpectedExecutionThrowTypeValues {

        //rethrow exception
        RETHROW,
        //wrap exception and throw
        THROW_IN_WRAPPER,
        //do not rethrow exceptino
        NO

    }

    def static valueOf(String name) {
        switch(name) {
            case "RETHROW":
                return ExpectedExecutionThrowTypeValues.RETHROW
            case "THROW_IN_WRAPPER":
                return ExpectedExecutionThrowTypeValues.THROW_IN_WRAPPER
            case "NO":
                 return ExpectedExecutionThrowTypeValues.NO
            default:
                return null
        }
    }

    def static RETHROW = ExpectedExecutionThrowTypeValues.RETHROW
    def static THROW_IN_WRAPPER = ExpectedExecutionThrowTypeValues.THROW_IN_WRAPPER
    def static NO = ExpectedExecutionThrowTypeValues.NO
}
