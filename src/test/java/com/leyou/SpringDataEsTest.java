package com.leyou;

import com.alibaba.fastjson.JSON;
import com.leyou.pojo.Goods;
import com.leyou.repository.GoodsRespository;
import org.apache.commons.beanutils.BeanUtils;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchResultMapper;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.aggregation.impl.AggregatedPageImpl;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SpringDataEsTest {
    @Autowired
    private ElasticsearchTemplate esTemplate;
    @Autowired
    private GoodsRespository goodsRepository;

    @Test
    public void testAddIndex(){
        esTemplate.createIndex(Goods.class);
    }
    @Test
    public void testAddMaping(){
        esTemplate.putMapping(Goods.class);
    }
    @Test
    public void testAddBulkDoc(){
        List<Goods> list = new ArrayList<>();
        list.add(new Goods("1", "小米手机7", "手机", "小米", 3299.00,"http://image.leyou.com/13123.jpg"));
        list.add(new Goods("2", "坚果手机R1", "手机", "锤子", 3699.00,"http://image.leyou.com/13123.jpg"));
        list.add(new Goods("3", "华为META10", "手机", "华为", 4499.00,"http://image.leyou.com/13123.jpg"));
        list.add(new Goods("4", "小米Mix2S", "手机", "小米", 4299.00, "http://image.leyou.com/13123.jpg"));
        list.add(new Goods("5", "荣耀V10", "手机", "华为", 2799.00,"http://image.leyou.com/13123.jpg"));
        goodsRepository.saveAll(list);
    }
    @Test
    public void testQueryAll(){
        Iterable<Goods> list = goodsRepository.findAll();
        list.forEach(System.out::println);
    }
    @Test
    public void testQuery(){
        NativeSearchQueryBuilder nativeSearchQueryBuilder = new NativeSearchQueryBuilder();
        nativeSearchQueryBuilder.withQuery(QueryBuilders.matchAllQuery());
        nativeSearchQueryBuilder.withPageable(PageRequest.of(0,2));
        nativeSearchQueryBuilder.addAggregation(AggregationBuilders.terms("brandCount").field("brand"));
        AggregatedPage<Goods> aggregatedPage = esTemplate.queryForPage(nativeSearchQueryBuilder.build(),Goods.class);
        Terms terms = aggregatedPage.getAggregations().get("brandCount");
        List<? extends Terms.Bucket> buckets = terms.getBuckets();
        buckets.forEach(bucket->{
            System.out.println(bucket.getKeyAsString()+":"+bucket.getDocCount());
        });
    }

    public class SearchResultMapperImpl implements SearchResultMapper{

        @Override
        public <T> AggregatedPage<T> mapResults(SearchResponse response, Class<T> clazz, Pageable pageable) {
            long total = response.getHits().getTotalHits();
            Aggregations aggregations = response.getAggregations();
            String scrollId = response.getScrollId();
            float maxScore = response.getHits().getMaxScore();

            SearchHit[] hits = response.getHits().getHits();
            List<T> content = new ArrayList<>();
            for (SearchHit hit : hits) {
                String jsonString = hit.getSourceAsString();
                T t = JSON.parseObject(jsonString,clazz);
                Map<String, HighlightField> highlightFields = hit.getHighlightFields();
                HighlightField highlightField = highlightFields.get("title");
                Text[] fragments = highlightField.getFragments();
                if (fragments!=null&&fragments.length>0){
                    String title = fragments[0].toString();
                    try {
                        BeanUtils.copyProperty(t,"title",title);
                    }catch (Exception e){
                        System.out.println("123");
                    }

                }
                content.add(t);
            }
            return new AggregatedPageImpl<T>(content,pageable,total,aggregations,scrollId,maxScore);
        }
    }
//你们说的都对
    @Test
    public void testQue(){
        NativeSearchQueryBuilder nativeSearchQueryBuilder= new NativeSearchQueryBuilder();
        nativeSearchQueryBuilder.withQuery(QueryBuilders.termQuery("title","米"));

        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.preTags("<>");
        highlightBuilder.postTags("</span>");
        highlightBuilder.field("title");
        nativeSearchQueryBuilder.withHighlightBuilder(highlightBuilder);
        nativeSearchQueryBuilder.withHighlightFields(new HighlightBuilder.Field("title"));
        AggregatedPage<Goods> aggregatedPage = esTemplate.queryForPage(nativeSearchQueryBuilder.build(),Goods.class,new SearchResultMapperImpl());
        List<Goods> goodsList = aggregatedPage.getContent();
        goodsList.forEach(goods -> {
            System.out.println(goods);
        });
    }
}
