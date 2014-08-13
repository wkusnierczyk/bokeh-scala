package io.continuum.bokeh

import annotation.implicitNotFound
import breeze.linalg.DenseVector

class ColumnsRef extends HasFields {
    object source extends Field[DataSource]
    object columns extends Field[List[Symbol]]
}

sealed abstract class DataSource extends PlotObject {
    object column_names extends Field[List[String]]
    object selected extends Field[List[String]]

    def columns(columns: Symbol*): ColumnsRef =
        new ColumnsRef().source(this).columns(columns.toList)
}

@implicitNotFound(msg="Can't find ArrayLike type class for type ${A}.")
class ArrayLike[A]

object ArrayLike {
    implicit def SeqToArrayLike[T] = new ArrayLike[Seq[T]]
    implicit def ListToArrayLike[T] = new ArrayLike[List[T]]
    implicit def ArrayToArrayLike[T] = new ArrayLike[Array[T]]
    implicit def DenseVectorToArrayLike[T] = new ArrayLike[DenseVector[T]]
}

class ColumnDataSource extends DataSource {
    object data extends Field[Map[Symbol, Any]] // TODO: HList/HRecord

    def addColumn[A:ArrayLike](name: Symbol, array: A): SelfType = {
        data := data.value + (name -> array)
        this
    }
}