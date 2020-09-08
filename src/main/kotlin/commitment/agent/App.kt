package commitment.agent

fun main(args: Array<String>) {
    val grpcServerPort = System.getenv("SERVER_PORT")?.toInt() ?: 9099
    val server = GrpcServer(grpcServerPort)
    server.start()
    server.blockUntilShutdown()
}
