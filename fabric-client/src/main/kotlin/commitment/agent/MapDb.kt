package commitment.agent.fabric.client

import arrow.core.Either
import arrow.core.Left
import arrow.core.Right
import org.mapdb.DB
import org.mapdb.Serializer

class MapDb {
    fun start(db: DB, key: Int, initialValue: String, orgName: String): Either<Error, Unit> = try {
        val map = db
                .hashMap("accumulator", Serializer.INTEGER, Serializer.STRING)
                .createOrOpen()
        map[key] = initialValue
        db.commit()
        Right(Unit)
    } catch (e: Exception) {
        println("DB Error: Error initialising database ${e.stackTrace}\n")
        Left(Error("DB Error: Error initialising database ${e.message}\n"))
    }

    fun update(db: DB, key: Int, value: String, orgName: String): Either<Error, Unit> = try {
        val map = db
                .hashMap("accumulator", Serializer.INTEGER, Serializer.STRING)
                .createOrOpen()
        map[key] = value
        db.commit()
        Right(Unit)
    } catch (e: Exception) {
        println("DB Error: Error updating database ${e.stackTrace}\n")
        Left(Error("DB Error: Error updating database ${e.message}\n"))
    }

    fun get(db: DB, key: Int, orgName: String): Either<Error, String> {
	println("Getting accumulator for org $orgName for blockheight $key")
        val map = db
                .hashMap("accumulator", Serializer.INTEGER, Serializer.STRING)
                .createOrOpen()
        val value = map[key]
        return if (value != null) {
            Right(value)
        } else {
            println("DB Error: Key $key not found in db.\n")
            Left(Error("DB Error: Key $key not found in db.\n"))
        }
    }
}
