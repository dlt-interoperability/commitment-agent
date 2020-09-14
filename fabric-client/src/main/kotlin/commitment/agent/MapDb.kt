package commitment.agent.fabric.client

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import org.mapdb.DBMaker.fileDB
import org.mapdb.Serializer
import org.starcoin.rsa.RSAAccumulator

class MapDb {
    fun start(key: Int, initialValue: String): Either<Error, Unit> = try {
        val db = fileDB("file.db")
                .fileMmapEnable()
                .make()
        val map = db
                .hashMap("accumulator", Serializer.INTEGER, Serializer.STRING)
                .createOrOpen()
        map[key] = initialValue
        db.commit()
        db.close()
        Right(Unit)
    } catch (e: Exception) {
        println("DB Error: Error initialising database ${e.stackTrace}\n")
        Left(Error("DB Error: Error initialising database ${e.message}\n"))
    }

    fun update(key: Int, value: String): Either<Error, Unit> = try {
        val db = fileDB("file.db")
                .fileMmapEnable()
                .make()
        val map = db
                .hashMap("accumulator", Serializer.INTEGER, Serializer.STRING)
                .createOrOpen()
        map[key] = value
        db.commit()
        db.close()
        Right(Unit)
    } catch (e: Exception) {
        println("DB Error: Error updating database ${e.stackTrace}\n")
        Left(Error("DB Error: Error updating database ${e.message}\n"))
    }

    fun get(key: Int): Either<Error, String> {
        val db = fileDB("file.db")
                .fileMmapEnable()
                .make()
        val map = db
                .hashMap("accumulator", Serializer.INTEGER, Serializer.STRING)
                .createOrOpen()
        val value = map[key]
        db.close()
        return if (value != null) {
            Right(value)
        } else {
            println("DB Error: Key $key not found in db.\n")
            Left(Error("DB Error: Key $key not found in db.\n"))
        }
    }
}