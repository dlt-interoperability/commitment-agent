package commitment.agent
import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import org.mapdb.DBMaker.fileDB
import org.mapdb.Serializer
import org.starcoin.rsa.RSAAccumulator

class MapDb {
    fun start(initialValue: String) {
        println("Initialising DB")
        val db = fileDB("file.db")
                .fileMmapEnable()
                .make()
        println("DB created")
        val map = db
                .hashMap("accumulator", Serializer.INTEGER, Serializer.STRING)
                .createOrOpen()
        // Make JSON string of RSA accumulator and store that
        println("Storing empty accumulator")
        map[0] = initialValue
        db.commit()
        println("Closing DB connection")
        db.close()
    }

    fun update(key: Int, value: String) {
        val db = fileDB("file.db")
                .fileMmapEnable()
                .make()
        println("Connected to DB: $db")
        val map = db
                .hashMap("accumulator", Serializer.INTEGER, Serializer.STRING)
                .createOrOpen()
        println("Storing $value in key $key")
        map[key] = value
        db.commit()
        println("Closing DB connection")
        db.close()
    }

    fun get(key: Int): Either<Error, String> {
        val db = fileDB("file.db")
                .fileMmapEnable()
                .make()
        println("Connected to DB: $db")
        val map = db
                .hashMap("accumulator", Serializer.INTEGER, Serializer.STRING)
                .createOrOpen()
        println("Getting value for $key")
        val value = map[key]
        println("Closing DB connection")
        db.close()
        return if (value != null) {
            println("Returning value $value")
            Right(value)
        } else {
            println("DB Error: Key $key not found in db.\n")
            Left(Error("DB Error: Key $key not found in db.\n"))
        }
    }
}