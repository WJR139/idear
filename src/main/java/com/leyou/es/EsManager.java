package com.leyou.es;


import com.alibaba.fastjson.JSON;
import com.google.gson.Gson;
import com.leyou.pojo.Item;
import com.sun.org.apache.bcel.internal.generic.IREM;
import org.apache.http.HttpHost;

import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.FuzzyQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EsManager {
    RestHighLevelClient client = null;
    Gson gson = new Gson();
    @Before
    public void init()throws Exception{
        client = new RestHighLevelClient(
                RestClient.builder(

                       new HttpHost("127.0.0.1",9201,"http"),
                       new HttpHost("127.0.0.1",9202,"http1"),
                       new HttpHost("127.0.0.1",9203,"http")
                )
        );

    }

    @Test//新增
    public void testDoc()throws Exception{
        Item item = new Item("1","小米9手机","手机","小米",1199.0,"qwert");
        //indexRequest专门用来插入索引数据的对象
        IndexRequest request = new IndexRequest("item","docs",item.getId());
        //把对象转换成json字符串
        String jsonStringAlib = JSON.toJSONString(item);
        String jsonString = gson.toJson(item);
        request.source(jsonString, XContentType.JSON);
        client.index(request, RequestOptions.DEFAULT);
    }
    @Test//删除
    public void testDeleteDoc()throws Exception{
        DeleteRequest request = new DeleteRequest("item","docs","1");
        client.delete(request,RequestOptions.DEFAULT);
    }

    @Test//批量新增
    public void tstBulkAddDoc()throws Exception{
        List<Item> list = new ArrayList<>();
        list.add(new Item("1", "小米手机7", "手机", "小米", 3299.00,"http://image.leyou.com/13123.jpg"));
        list.add(new Item("2", "坚果手机R1", "手机", "锤子", 3699.00,"http://image.leyou.com/13123.jpg"));
        list.add(new Item("3", "华为META10", "手机", "华为", 4499.00,"http://image.leyou.com/13123.jpg"));
        list.add(new Item("4", "小米Mix2S", "手机", "小米", 4299.00, "http://image.leyou.com/13123.jpg"));
        list.add(new Item("5", "荣耀V10", "手机", "华为", 2799.00,"http://image.leyou.com/13123.jpg"));
        BulkRequest request = new BulkRequest();
        list.forEach(item -> {
            IndexRequest indexRequest = new IndexRequest("item","docs",item.getId());
            String jsonString = gson.toJson(item);
            indexRequest.source(jsonString,XContentType.JSON);
            request.add(indexRequest);
        });
        client.bulk(request,RequestOptions.DEFAULT);
    }

    @Test//查看索引
    public void testFindIndex()throws Exception{
        GetRequest request = new GetRequest("item","docs","1");
        GetResponse response = client.get(request,RequestOptions.DEFAULT);
        String source = response.getSourceAsString();
        Item parse = JSON.parseObject(source,Item.class);
        System.out.println(parse);
    }
    @Test
    public void testSearch()throws Exception{
//        构建一个用来查询的对象
        SearchRequest searchRequest = new SearchRequest("item").types("docs");
//        构建查询方式
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
//        添加查询条件
        searchSourceBuilder.query(QueryBuilders.matchAllQuery());
        searchSourceBuilder.sort("price",SortOrder.DESC);
        searchSourceBuilder.size(0);
        searchSourceBuilder.aggregation(AggregationBuilders.terms("bradAgg").field("brand"));
// 放入到request
        searchRequest.source(searchSourceBuilder);
//        搜索
        SearchResponse response = client.search(searchRequest,RequestOptions.DEFAULT);
        Aggregations aggregations = response.getAggregations();
        Terms terms= aggregations.get("bradAgg");
        List<? extends Terms.Bucket> buckets = terms.getBuckets();
        buckets.forEach(bucket->{
            System.out.println(bucket.getKeyAsString()+":"+bucket.getDocCount());
        });
//        解析
        SearchHits hits = response.getHits();
        SearchHit[] searchHits = hits.getHits();
        for (SearchHit searchHit : searchHits) {
            String jsonString = searchHit.getSourceAsString();
            Item item =gson.fromJson(jsonString,Item.class);
            System.out.println(item);
        }
    }
    @Test
    public void testhighLight()throws Exception{
        SearchRequest request = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchQuery("title","小米"));
        //高亮
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field("title");
        highlightBuilder.preTags("<span style='color:red'>");
        highlightBuilder.postTags("</span>");
        searchSourceBuilder.highlighter(highlightBuilder);
        request.source(searchSourceBuilder);
        SearchResponse response = client.search(request,RequestOptions.DEFAULT);
        SearchHits hits = response.getHits();
        SearchHit[] searchHits = hits.getHits();
        for (SearchHit searchHit : searchHits) {
            String jsonString = searchHit.getSourceAsString();
            Item item = gson.fromJson(jsonString,Item.class);
            Map<String, HighlightField> highlightFields = searchHit.getHighlightFields();
            HighlightField highlightField = highlightFields.get("title");
            Text[] fragments = highlightField.getFragments();
            if (fragments!=null&&fragments.length>0){
                String title = fragments[0].toString();
                item.setTitle(title);//把item的title替换成高亮的数据
                System.out.println(title);
            }
        }

    }

    @After
    public void end()throws Exception{
        client.close();
    }
}
