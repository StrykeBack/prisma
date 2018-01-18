package cool.graph.api.mutations.mutations

import cool.graph.api.ApiDependencies
import cool.graph.api.database.DataResolver
import cool.graph.api.database.mutactions.mutactions.UpsertDataItem
import cool.graph.api.database.mutactions.{MutactionGroup, TransactionMutaction}
import cool.graph.api.mutations._
import cool.graph.cuid.Cuid
import cool.graph.shared.models.{Model, Project}
import sangria.schema

import scala.concurrent.Future

case class Upsert(
    model: Model,
    project: Project,
    args: schema.Args,
    dataResolver: DataResolver,
    allowSettingManagedFields: Boolean = false
)(implicit apiDependencies: ApiDependencies)
    extends SingleItemClientMutation {

  import apiDependencies.system.dispatcher

  val where = CoolArgs(args.raw).extractNodeSelectorFromWhereField(model)

  val idOfNewItem = Cuid.createCuid()
  val createArgs  = CoolArgs(args.raw).createArgumentsAsCoolArgs.generateCreateArgs(model, idOfNewItem)
  val updateArgs  = CoolArgs(args.raw).updateArgumentsAsCoolArgs.generateUpdateArgs(model)

  val upsert = UpsertDataItem(project, where, createArgs, updateArgs)

  override def prepareMutactions(): Future[List[MutactionGroup]] = {
    val transaction = TransactionMutaction(List(upsert), dataResolver)
    Future.successful(List(MutactionGroup(List(transaction), async = false)))
  }

//  override def prepareMutactions(): Future[List[MutactionGroup]] = {
//
//    val sqlMutactions        = SqlMutactions(dataResolver).getMutactionsForUpsert(CoolArgs(args.raw), createArgs, updateArgs, idOfNewItem, where)
//    val transactionMutaction = TransactionMutaction(sqlMutactions, dataResolver)
////    val subscriptionMutactions = SubscriptionEvents.extractFromSqlMutactions(project, mutationId, sqlMutactions).toList
////    val sssActions             = ServerSideSubscription.extractFromMutactions(project, sqlMutactions, requestId = "").toList
//
//    Future(
//      List(
//        MutactionGroup(mutactions = List(transactionMutaction), async = false)
////    ,  MutactionGroup(mutactions = sssActions ++ subscriptionMutactions, async = true)
//      ))
////    val transaction = TransactionMutaction(List(upsert), dataResolver)
////    Future.successful(List(MutactionGroup(List(transaction), async = false)))
//  }

  override def getReturnValue: Future[ReturnValueResult] = {
    val newWhere = updateArgs.raw.get(where.field.name) match {
      case Some(_) => updateArgs.extractNodeSelector(model)
      case None    => where
    }

    val uniques = Vector(NodeSelector.forId(model, idOfNewItem), newWhere)
    dataResolver.resolveByUniques(model, uniques).map { items =>
      items.headOption match {
        case Some(item) => ReturnValue(item)
        case None       => sys.error("Could not find an item after an Upsert. This should not be possible.") // Todo: what should we do here?
      }
    }
  }
}