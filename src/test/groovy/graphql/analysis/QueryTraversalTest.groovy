package graphql.analysis

import graphql.TestUtil
import graphql.language.Document
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import spock.lang.Specification
import spock.lang.Unroll

class QueryTraversalTest extends Specification {


    Document createQuery(String query) {
        Parser parser = new Parser()
        parser.parseDocument(query)
    }

    QueryTraversal createQueryTraversal(Document document, GraphQLSchema schema, Map variables = [:]) {
        QueryTraversal queryTraversal = new QueryTraversal(
                schema,
                document,
                null,
                variables
        )
        return queryTraversal
    }

    def "test preOrder order"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                subFoo: String  
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {foo { subFoo} bar }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal.visitPreOrder(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parentType.name == "Query" })
        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parentType.name == "Foo" &&
                    it.parentEnvironment.field.name == "foo" && it.parentEnvironment.fieldDefinition.type.name == "Foo"
        })
        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })

    }

    def "test postOrder order"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                subFoo: String  
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {foo { subFoo} bar }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal.visitPostOrder(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parentType.name == "Foo" &&
                    it.parentEnvironment.field.name == "foo" && it.parentEnvironment.fieldDefinition.type.name == "Foo"
        })
        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parentType.name == "Query" })
        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })

    }


    @Unroll
    def "field with arguments: (#order)"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo(arg1: String, arg2: Boolean): String
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            query myQuery(\$myVar: String){foo(arg1: \$myVar, arg2: true)} 
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, ['myVar': 'hello'])
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it ->
            it.field.name == "foo" &&
                    it.arguments == ['arg1': 'hello', 'arg2': true]
        })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'
    }

    @Unroll
    def "simple query: (#order)"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                subFoo: String  
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {bar foo { subFoo} }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parentType.name == "Foo" &&
                    it.parentEnvironment.field.name == "foo" && it.parentEnvironment.fieldDefinition.type.name == "Foo"
        })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'
    }

    @Unroll
    def "query with inline fragment (#order)"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                subFoo: String  
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {
                bar 
                ... on Query {
                    foo 
                    { subFoo
                    } 
                }
            }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parentType.name == "Foo" &&
                    it.parentEnvironment.field.name == "foo" && it.parentEnvironment.fieldDefinition.type.name == "Foo"
        })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'

    }

    @Unroll
    def "query with inline fragment without condition (#order)"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                subFoo: String  
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {
                bar 
                ... {
                    foo 
                    { subFoo
                    } 
                }
            }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parentType.name == "Foo" &&
                    it.parentEnvironment.field.name == "foo" && it.parentEnvironment.fieldDefinition.type.name == "Foo"
        })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'
    }


    @Unroll
    def "query with fragment (#order)"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                subFoo: String  
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {
                bar 
                ...Test
            }
            fragment Test on Query {
                foo 
                { subFoo
                } 
            }
            
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it ->
            it.field.name == "subFoo" && it.fieldDefinition.type.name == "String" &&
                    it.parentType.name == "Foo" &&
                    it.parentEnvironment.field.name == "foo" && it.parentEnvironment.fieldDefinition.type.name == "Foo"
        })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'

    }

    @Unroll
    def "query with skipped fields (#order)"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo 
                bar: String
            }
            type Foo {
                subFoo: String  
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {
                bar 
                ...Test @skip(if: true)
            }
            fragment Test on Query {
                foo 
                { subFoo
                } 
            }
            
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })
        0 * visitor.visitField(*_)

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'
    }

    @Unroll
    def "query with skipped fields and variables (#order)"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo 
                bar: String
            }
            type Foo {
                subFoo: String  
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            query MyQuery(\$variableFoo: Boolean) {
                bar 
                ...Test @skip(if: \$variableFoo)
            }
            fragment Test on Query {
                foo 
                { subFoo
                } 
            }
            
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, [variableFoo: true])
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })
        0 * visitor.visitField(*_)

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'
    }

    @Unroll
    def "nested fragments (#order)"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo1 
                bar: String
            }
            type Foo1 {
                string: String  
                subFoo: Foo2 
            }
            type Foo2 {
                otherString: String
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            query MyQuery(\$variableFoo: Boolean) {
                bar 
                ...Test @include(if: \$variableFoo)
            }
            fragment Test on Query {
                bar
                foo {
                    ...OnFoo1
                }
            }
            
            fragment OnFoo1 on Foo1 {
                string
                subFoo {
                    ... on Foo2 {
                       otherString 
                    }
                }
            }
            
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema, [variableFoo: true])
        when:
        queryTraversal."$visitFn"(visitor)

        then:
        2 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "bar" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "foo" && it.fieldDefinition.type.name == "Foo1" && it.parentType.name == "Query" })
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "string" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Foo1" })
        1 * visitor.visitField({ QueryVisitorEnvironment it -> it.field.name == "subFoo" && it.fieldDefinition.type.name == "Foo2" && it.parentType.name == "Foo1" })
        1 * visitor.visitField({ QueryVisitorEnvironment it ->
            QueryVisitorEnvironment secondParent = it.parentEnvironment.parentEnvironment
            it.field.name == "otherString" && it.fieldDefinition.type.name == "String" && it.parentType.name == "Foo2" &&
                    it.parentEnvironment.field.name == "subFoo" && it.parentEnvironment.fieldDefinition.type.name == "Foo2" && it.parentEnvironment.parentType.name == "Foo1" &&
                    secondParent.field.name == "foo" && secondParent.fieldDefinition.type.name == "Foo1" && secondParent.parentType.name == "Query"
        })

        where:
        order       | visitFn
        'postOrder' | 'visitPostOrder'
        'preOrder'  | 'visitPreOrder'

    }


    def "reduce preOrder"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                subFoo: String  
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {foo { subFoo} bar }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        QueryReducer reducer = Mock(QueryReducer)
        when:
        def result = queryTraversal.reducePreOrder(reducer, 1)

        then:
        1 * reducer.reduceField({ it.field.name == "foo" }, 1) >> 2
        then:
        1 * reducer.reduceField({ it.field.name == "subFoo" }, 2) >> 3
        then:
        1 * reducer.reduceField({ it.field.name == "bar" }, 3) >> 4
        result == 4

    }


    def "reduce postOrder"() {
        given:
        def schema = TestUtil.schema("""
            type Query{
                foo: Foo
                bar: String
            }
            type Foo {
                subFoo: String  
            }
        """)
        def visitor = Mock(QueryVisitor)
        def query = createQuery("""
            {foo { subFoo} bar }
            """)
        QueryTraversal queryTraversal = createQueryTraversal(query, schema)
        QueryReducer reducer = Mock(QueryReducer)
        when:
        def result = queryTraversal.reducePostOrder(reducer, 1)

        then:
        1 * reducer.reduceField({ it.field.name == "subFoo" }, 1) >> 2
        then:
        1 * reducer.reduceField({ it.field.name == "foo" }, 2) >> 3
        then:
        1 * reducer.reduceField({ it.field.name == "bar" }, 3) >> 4
        result == 4

    }

}
