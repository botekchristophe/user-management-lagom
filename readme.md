# user-management-lagom

This project is an example of implementation for a basic user management using Lagom Framework 1.4.5.

## Get started

```git
git clone https://github.com/botekchristophe/user-management-lagom.git
```

```
cd user-management-lagom
```

```
sbt runAll
```

```curl
curl -X POST \
  http://localhost:9000/api/users \
  -d '{
	"username":"foo",
	"password": "barbarbar"
  }'
```

```curl
curl -X PUT http://localhost:90000/api/users/{{id}}/verify
```

## API

```scala
  def descriptor: Descriptor = {
    import Service._
    named("user").withCalls(
      restCall(Method.GET,    "/api/users",              getUsers _),
      restCall(Method.POST,   "/api/users",              createUser _),
      restCall(Method.GET,    "/api/users/:id",          getUser _),
      restCall(Method.DELETE, "/api/users/:id",          deleteUser _),
      restCall(Method.PUT,    "/api/users/:id/verify",   verifyUser _),
      restCall(Method.PUT,    "/api/users/:id/unverify", unVerifyUser _),
      restCall(Method.POST,   "/api/users/auth",         getUserAuth _),
      restCall(Method.POST,   "/api/users/auth/grant",   userLogin _),
      restCall(Method.POST,   "/api/users/auth/revoke",  revokeToken _),
      restCall(Method.POST,   "/api/users/auth/refresh", refreshToken _)
    )
  }
```

## Persistence entity

![Image of User FSM](https://raw.githubusercontent.com/botekchristophe/user-management-lagom/master/UserFSM.png)

## ReadSide - Cassandra

In order to demonstrate Cassandra Readside with Lagom, two tables are being updated by processing the event stream.

### users table

| id    | username | status     |
|-------|----------|------------|
| $UUID | john     | VERIFIED   |
| $UUID | Maddie   | UNVERIFIED |

### sessions table

| access_token | refresh_token |   userid   |
|--------------|---------------|------------|
|     $UUID    |     $UUID     |    $UUID   |
|     $UUID    |     $UUID     |    $UUID   |


## Further work

### error management
If I have the time I will remove all the error thrown and use Eithers.
The model would look like that:

```
Either[Error, T]
```

### unit testing
The unit test kit provided by Lagom are really interesting.

### Model evolution
One of the biggest issue when going from development to production is that the understanding of the business changes over time.
Lagom helps with that by providing Schema evolution:
https://www.lagomframework.com/documentation/1.4.x/scala/Serialization.html#Schema-Evolution