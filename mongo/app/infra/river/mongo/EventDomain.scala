package infra.river.mongo

import org.joda.time.DateTime
import infra.mongo.MongoDomain
import infra.river.data.Event


case class EventDomain(action: String,
                       userId: Option[String],

                       contexts: Map[String, String],
                       artifacts: Map[String, String],

                       timestamp: DateTime,

                       _id: MongoDomain.Oid.Id
                        ) extends MongoDomain.Oid with Event