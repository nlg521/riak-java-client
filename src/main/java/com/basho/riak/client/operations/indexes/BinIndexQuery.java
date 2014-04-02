/*
 * Copyright 2014 Basho Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.basho.riak.client.operations.indexes;

import com.basho.riak.client.core.FailureInfo;
import com.basho.riak.client.core.RiakCluster;
import com.basho.riak.client.core.RiakFuture;
import com.basho.riak.client.core.operations.SecondaryIndexQueryOperation;
import com.basho.riak.client.operations.CoreFutureAdapter;
import com.basho.riak.client.operations.indexes.SecondaryIndexQuery.IndexConverter;
import com.basho.riak.client.query.Location;
import com.basho.riak.client.util.BinaryValue;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 *
 * @author Brian Roach <roach at basho dot com>
 */
public class BinIndexQuery extends SecondaryIndexQuery<String, BinIndexQuery.Response, BinIndexQuery>
{
    private final Charset charset;
    private final IndexConverter<String> converter;

    protected  BinIndexQuery(Init<String,?> builder)
    {
        super(builder);
        this.charset = builder.charset;
        this.converter = new IndexConverter<String>() 
        {
            @Override
            public String convert(BinaryValue input)
            {
                return input.toString(charset);
            }

            @Override
            public BinaryValue convert(String input)
            {
                return BinaryValue.create(input, charset);
            }
        };
    }

    @Override 
    protected IndexConverter<String> getConverter()
    {
        return converter;
    }

    @Override
    protected Response doExecute(RiakCluster cluster) throws ExecutionException, InterruptedException
    {
        RiakFuture<Response, BinIndexQuery> future = doExecuteAsync(cluster);
        
        future.await();
        
        if (future.isSuccess())
        {
            return future.get();
        }
        else
        {
            throw new ExecutionException(future.cause().getCause());
        }
    }

    @Override
    protected RiakFuture<Response, BinIndexQuery> doExecuteAsync(RiakCluster cluster)
    {
        RiakFuture<SecondaryIndexQueryOperation.Response, SecondaryIndexQueryOperation.Query> coreFuture =
            executeCoreAsync(cluster);
        
        BinQueryFuture future = new BinQueryFuture(coreFuture);
        coreFuture.addListener(future);
        return future;
    }

    protected final class BinQueryFuture extends CoreFutureAdapter<Response, BinIndexQuery, SecondaryIndexQueryOperation.Response, SecondaryIndexQueryOperation.Query>
    {
        public BinQueryFuture(RiakFuture<SecondaryIndexQueryOperation.Response, SecondaryIndexQueryOperation.Query> coreFuture)
        {
            super(coreFuture);
        }
        
        @Override
        protected Response convertResponse(SecondaryIndexQueryOperation.Response coreResponse)
        {
            return new Response(coreResponse, converter);
        }

        @Override
        protected FailureInfo<BinIndexQuery> convertFailureInfo(FailureInfo<SecondaryIndexQueryOperation.Query> coreQueryInfo)
        {
            return new FailureInfo<BinIndexQuery>(coreQueryInfo.getCause(), BinIndexQuery.this);
        }
    }
    
    protected static abstract class Init<S, T extends Init<S,T>> extends SecondaryIndexQuery.Init<S,T>
    {
        private Charset charset = Charset.defaultCharset();

        public Init(Location location, String indexName, S start, S end)
        {
            super(location, indexName + Type._BIN, start, end);
        }

        public Init(Location location, String indexName, S match)
        {
            super(location, indexName + Type._BIN, match);
        }

        T withCharacterSet(Charset charset)
        {
            this.charset = charset;
            return self();
        }

    }

    public static class Builder extends Init<String, Builder>
    {

        public Builder(Location location, String indexName, String start, String end)
        {
            super(location, indexName, start, end);
        }

        public Builder(Location location, String indexName, String match)
        {
            super(location, indexName, match);
        }

        @Override
        protected Builder self()
        {
            return this;
        }

        public BinIndexQuery build()
        {
            return new BinIndexQuery(this);
        }

    }
    
    public static class Response extends SecondaryIndexQuery.Response<String>
    {
        protected Response(SecondaryIndexQueryOperation.Response coreResponse, IndexConverter<String> converter)
        {
            super(coreResponse, converter);
        }
        
        @Override
        public List<Entry> getEntries()
        {
            List<Entry> convertedList = new ArrayList<Entry>();
            for (SecondaryIndexQueryOperation.Response.Entry e : coreResponse.getEntryList())
            {
                Location loc = getLocationFromCoreEntry(e);
                Entry ce = new Entry(loc, e.getIndexKey(), converter);
                convertedList.add(ce);
            }
            return convertedList;
        }

        public class Entry extends SecondaryIndexQuery.Response.Entry<String>
        {
            protected Entry(Location riakObjectLocation, BinaryValue indexKey, IndexConverter<String> converter)
            {
                super(riakObjectLocation, indexKey, converter);
            }

        }
    }
}