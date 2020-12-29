public class ExpectedExceptionThrowType {
    public static enum ExpectedExceptionThrowTypeValues {

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
                return ExpectedExceptionThrowTypeValues.RETHROW
            case "THROW_IN_WRAPPER":
                return ExpectedExceptionThrowTypeValues.THROW_IN_WRAPPER
            case "NO":
                 return ExpectedExceptionThrowTypeValues.NO
            default:
                return null
        }
    }

    public static ExpectedExceptionThrowType RETHROW = ExpectedExceptionThrowTypeValues.RETHROW
    public static ExpectedExceptionThrowType THROW_IN_WRAPPER = ExpectedExceptionThrowTypeValues.THROW_IN_WRAPPER
    public static ExpectedExceptionThrowType NO = ExpectedExceptionThrowTypeValues.NO
}
