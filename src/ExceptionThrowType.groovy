public class ExceptionThrowType {
    public static enum ExceptionThrowTypeValues {

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
                return ExceptionThrowTypeValues.RETHROW
            case "THROW_IN_WRAPPER":
                return ExceptionThrowTypeValues.THROW_IN_WRAPPER
            case "NO":
                 return ExceptionThrowTypeValues.NO
            default:
                return null
        }
    }

    def static RETHROW = ExceptionThrowTypeValues.RETHROW
    def static THROW_IN_WRAPPER = ExceptionThrowTypeValues.THROW_IN_WRAPPER
    def static NO = ExceptionThrowTypeValues.NO
}
