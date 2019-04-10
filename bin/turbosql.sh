
# JAVA_HOME.
    if [ "${JAVA_HOME:-}" = "" ]; then
        JAVA=`type -p java`
        RETCODE=$?

        if [ $RETCODE -ne 0 ]; then
            echo $0", ERROR:"
            echo "JAVA_HOME environment variable is not found."
            echo "Please point JAVA_HOME variable to location of JDK 1.8 or later."
            echo "You can also download latest JDK at http://java.com/download"

            exit 1
        fi

        JAVA_HOME=
    else
        JAVA=${JAVA_HOME}/bin/java
fi
