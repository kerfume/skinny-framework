package skinny.orm.feature

import scalikejdbc._
import skinny._
import skinny.orm._
import skinny.orm.feature.associations._

/**
 * Provides auto-generated CRUD feature.
 *
 * @tparam Entity entity
 */
trait CRUDFeature[Entity]
  extends CRUDFeatureWithId[Long, Entity]

trait CRUDFeatureWithId[Id, Entity]
    extends SkinnyMapperBase[Entity]
    with SkinnyModel[Id, Entity]
    with ConnectionPoolFeature
    with AutoSessionFeature
    with AssociationsWithIdFeature[Id, Entity]
    with JoinsFeature[Entity]
    with IdFeature[Id]
    with NoIdCUDFeature[Entity]
    with IncludesFeatureWithId[Id, Entity]
    with QueryingFeatureWithId[Id, Entity]
    with FinderFeatureWithId[Id, Entity]
    with DynamicTableNameFeatureWithId[Id, Entity]
    with StrongParametersFeature {

  override def joins[Id](associations: Association[_]*): CRUDFeatureWithId[Id, Entity] = {
    val _self = this
    val _associations = associations

    // creates new instance but ideally this should be more DRY & safe implementation
    new CRUDFeatureWithId[Id, Entity] {
      override protected val underlying = _self
      override def defaultAlias = _self.defaultAlias

      override def tableName = _self.tableName
      override def columnNames = _self.columnNames

      override def primaryKeyField = _self.primaryKeyField
      override def primaryKeyFieldName = _self.primaryKeyFieldName

      override def rawValueToId(value: Any) = _self.rawValueToId(value).asInstanceOf[Id]
      // override def idToRawValue(id: Id) = _self.idToRawValue(id)
      override def idToRawValue(id: Id) = id

      override val associations = _self.associations ++ _associations

      override val defaultJoinDefinitions = _self.defaultJoinDefinitions
      override val defaultBelongsToExtractors = _self.defaultBelongsToExtractors
      override val defaultHasOneExtractors = _self.defaultHasOneExtractors
      override val defaultOneToManyExtractors = _self.defaultOneToManyExtractors

      override def autoSession = underlying.autoSession
      override def connectionPoolName = underlying.connectionPoolName
      override def connectionPool = underlying.connectionPool

      override def defaultScope(alias: Alias[Entity]) = _self.defaultScope(alias)
      //override def singleSelectQuery = _self.singleSelectQuery

      def extract(rs: WrappedResultSet, n: ResultName[Entity]) = underlying.extract(rs, n)
    }
  }

  /**
   * Replaces table name on runtime.
   *
   * @param tableName table name
   * @return self
   */
  override def withTableName(tableName: String): CRUDFeatureWithId[Id, Entity] = {
    val _self = this
    val dynamicTableName = tableName

    // creates new instance but ideally this should be more DRY & safe implementation
    new CRUDFeatureWithId[Id, Entity] {
      override protected val underlying = _self
      override def defaultAlias = _self.defaultAlias

      // overwritten table name
      override val tableName = dynamicTableName
      override def columnNames = _self.columnNames

      override def primaryKeyField = _self.primaryKeyField
      override def primaryKeyFieldName = _self.primaryKeyFieldName

      override def rawValueToId(value: Any) = _self.rawValueToId(value)
      override def idToRawValue(id: Id) = _self.idToRawValue(id)

      override val associations = _self.associations

      override val defaultJoinDefinitions = _self.defaultJoinDefinitions
      override val defaultBelongsToExtractors = _self.defaultBelongsToExtractors
      override val defaultHasOneExtractors = _self.defaultHasOneExtractors
      override val defaultOneToManyExtractors = _self.defaultOneToManyExtractors
      override def autoSession = underlying.autoSession
      override def connectionPoolName = underlying.connectionPoolName
      override def connectionPool = underlying.connectionPool

      override def defaultScope(alias: Alias[Entity]) = _self.defaultScope(alias)
      //override def singleSelectQuery = _self.singleSelectQuery

      def extract(rs: WrappedResultSet, n: ResultName[Entity]) = underlying.extract(rs, n)
    }
  }

  /**
   * #updateBy pre-execution handler.
   */
  type BeforeCreateHandler = (DBSession, Seq[(SQLSyntax, Any)]) => Unit

  /**
   * #updateBy post-execution handler.
   */
  type AfterCreateHandler = (DBSession, Seq[(SQLSyntax, Any)], Option[Id]) => Unit

  /**
   * Registered beforeUpdateByHandlers.
   */
  protected val beforeCreateHandlers = new scala.collection.mutable.ListBuffer[BeforeCreateHandler]

  /**
   * Registered afterUpdateByHandlers.
   */
  protected val afterCreateHandlers = new scala.collection.mutable.ListBuffer[AfterCreateHandler]

  /**
   * #createWithNamedValues pre-execution.
   *
   * @param handler handler
   */
  def beforeCreate(handler: BeforeCreateHandler): Unit = beforeCreateHandlers.append(handler)

  /**
   * #createWithNamedValues post-execution.
   *
   * @param handler handler
   */
  def afterCreate(handler: AfterCreateHandler): Unit = afterCreateHandlers.append(handler)

  /**
   * #createWithNamedValues pre-execution.
   *
   * @param namedValues named values
   */
  @deprecated(message = "Use beforeCreate(handler) instead", since = "1.3.12")
  protected def beforeCreate(namedValues: Seq[(SQLSyntax, Any)])(implicit s: DBSession = autoSession): Unit = {}

  /**
   * #createWithNamedValues post-execution.
   *
   * @param namedValues named values
   * @param generatedId generated id
   */
  @deprecated(message = "Use afterCreate(handler) instead", since = "1.3.12")
  protected def afterCreate(namedValues: Seq[(SQLSyntax, Any)], generatedId: Option[Id])(
    implicit s: DBSession = autoSession): Unit = {}

  /**
   * Creates a new entity with named values.
   *
   * @param namedValues named values
   * @param s db session
   * @return created count
   */
  override def createWithNamedValues(namedValues: (SQLSyntax, Any)*)(implicit s: DBSession = autoSession): Id = {
    val (allNamedValues: Seq[(SQLSyntax, Any)], generatedIdOpt: Option[Id]) = {
      val primaryKey = column.field(primaryKeyFieldName)
      if (namedValues.exists(_._1 == primaryKey)) {
        // already primary key is set
        val passedIdOpt: Option[Id] = namedValues.find(_._1 == primaryKey).flatMap {
          case (_, v) =>
            try Option(v.asInstanceOf[Id])
            catch {
              case e: ClassCastException =>
                v match {
                  case null | None => None
                  case Some(v) => Some(rawValueToId(v))
                  case v => Some(rawValueToId(v))
                }
            }
        }
        val namedValuesWithRawId: Seq[(SQLSyntax, Any)] = namedValues.map {
          case (k, v) if k == primaryKey => {
            try k -> idToRawValue(v.asInstanceOf[Id])
            catch { case e: ClassCastException => k -> v }
          }
          case (k, v) => k -> v
        }
        (mergeNamedValuesForCreation(namedValuesWithRawId), passedIdOpt)
      } else if (useExternalIdGenerator) {
        // generate new primary key value using external key generator
        val newId = generateId
        (mergeNamedValuesForCreation(namedValues) :+ (primaryKey -> idToRawValue(newId)), Some(newId))
      } else {
        // no generated key
        (mergeNamedValuesForCreation(namedValues), None)
      }
    }

    beforeCreate(allNamedValues)
    beforeCreateHandlers.foreach(_.apply(s, allNamedValues))

    if (useAutoIncrementPrimaryKey && !useExternalIdGenerator) {
      val id = withSQL { insert.into(this).namedValues(allNamedValues: _*) }.updateAndReturnGeneratedKey.apply()
      afterCreate(allNamedValues, Some(id).map(_.asInstanceOf[Id]))
      afterCreateHandlers.foreach(_.apply(s, allNamedValues, Some(id).map(_.asInstanceOf[Id])))
      convertAutoGeneratedIdToId(id).getOrElse(
        throw new IllegalStateException(s"Failed to retrieve auto-generated primary key value from ${tableName} when insertion."))
    } else {
      withSQL { insert.into(this).namedValues(allNamedValues: _*) }.update.apply()
      afterCreate(allNamedValues, generatedIdOpt)
      afterCreateHandlers.foreach(_.apply(s, allNamedValues, generatedIdOpt))
      generatedIdOpt.getOrElse(null.asInstanceOf[Id])
    }
  }

  /**
   * Returns update query builder with primary key.
   *
   * @param id primary key
   * @return update query builder
   */
  def updateById(id: Id): UpdateOperationBuilder = updateBy(byId(id))

  /**
   * Updates entities with parameters.
   *
   * @param id primary key
   * @param parameters parameters
   * @return updated count
   */
  def updateById(id: Id, parameters: PermittedStrongParameters): Int = updateById(id).withPermittedAttributes(parameters)

  /**
   * Returns a query part which represents primary key search condition.
   *
   * @param id primary key
   * @return query part
   */
  protected def byId(id: Id): SQLSyntax = sqls.eq(column.field(primaryKeyFieldName), idToRawValue(id))

  /**
   * Deletes a single entity by primary key.
   *
   * @param id primary key
   * @param s db session
   * @return deleted count
   */
  def deleteById(id: Id)(implicit s: DBSession = autoSession): Int = deleteBy(byId(id))

  override def createWithPermittedAttributes(strongParameters: PermittedStrongParameters)(implicit s: DBSession): Id = {
    if (useAutoIncrementPrimaryKey || useExternalIdGenerator) {
      createWithNamedValues(namedValuesForCreation(strongParameters): _*)
    } else {
      // this method won't return passed ID value
      super[NoIdCUDFeature].createWithPermittedAttributes(strongParameters)
      // extract ID value from passed params
      strongParameters.params.get(primaryKeyFieldName).map(_._1)
        .map(value => rawValueToId(value)).getOrElse(null.asInstanceOf[Id])
    }
  }

  override def createWithAttributes(parameters: (Symbol, Any)*)(implicit s: DBSession): Id = {
    super.createWithAttributes(parameters: _*).asInstanceOf[Id]
  }

  // ---------------------
  // for SkinnyModel

  override def createNewModel(parameters: PermittedStrongParameters): Id = createWithPermittedAttributes(parameters)

  override def findAllModels() = findAll()

  override def countAllModels(): Long = count()

  override def findModels(pageSize: Int, pageNo: Int) = findAllWithLimitOffset(pageSize, pageSize * (pageNo - 1))

  override def findModel(id: Id) = findById(id)

  override def updateModelById(id: Id, parameters: PermittedStrongParameters) = updateById(id, parameters)

  override def deleteModelById(id: Id) = deleteById(id)

}
