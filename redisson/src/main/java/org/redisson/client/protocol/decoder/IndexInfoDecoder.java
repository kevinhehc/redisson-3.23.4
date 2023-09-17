/**
 * Copyright (c) 2013-2022 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.client.protocol.decoder;

import org.redisson.api.search.index.IndexInfo;
import org.redisson.client.handler.State;

import java.util.List;
import java.util.Map;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class IndexInfoDecoder implements MultiDecoder<Object> {
    @Override
    public Object decode(List<Object> parts, State state) {
        if (!parts.isEmpty() && parts.get(0) instanceof Map) {
            return parts;
        }

        Map<String, Object> result = MultiDecoder.newLinkedHashMap(parts.size()/2);
        for (int i = 0; i < parts.size(); i++) {
            if (i % 2 != 0) {
                result.put((String) parts.get(i-1), parts.get(i));
            }
        }

        if (state.getLevel() == 0) {
            IndexInfo ii = new IndexInfo();
            ii.setName((String) result.get("index_name"));
            ii.setOptions((Map<String, Object>) result.get("index_options"));
            ii.setDefinition((Map<String, Object>) result.get("index_definition"));
            ii.setAttributes((List<Map<String, Object>>) result.get("attributes"));
            ii.setDocs(toLong(result, "num_docs"));
            ii.setMaxDocId(toLong(result, "max_doc_id"));
            ii.setTerms(toLong(result, "num_terms"));
            ii.setRecords(toLong(result, "num_records"));
            ii.setInvertedSize(Double.valueOf(result.get("inverted_sz_mb").toString()));
            ii.setVectorIndexSize(Double.valueOf(result.get("vector_index_sz_mb").toString()));
            ii.setTotalInvertedIndexBlocks(Double.valueOf(result.get("total_inverted_index_blocks").toString()));
            ii.setOffsetVectorsSize(Double.valueOf(result.get("offset_vectors_sz_mb").toString()));
            ii.setDocTableSize(Double.valueOf(result.get("doc_table_size_mb").toString()));
            ii.setSortableValuesSize(Double.valueOf(result.get("sortable_values_size_mb").toString()));
            ii.setKeyTableSize(Double.valueOf(result.get("key_table_size_mb").toString()));
            ii.setRecordsPerDocAverage(toLong(result, "records_per_doc_avg"));
            ii.setBytesPerRecordAverage(toLong(result, "bytes_per_record_avg"));
            ii.setOffsetsPerTermAverage(toLong(result, "offsets_per_term_avg"));
            ii.setOffsetBitsPerRecordAverage(toLong(result, "offset_bits_per_record_avg"));
            ii.setHashIndexingFailures(toLong(result, "hash_indexing_failures"));
            ii.setTotalIndexingTime(Double.valueOf(result.get("total_indexing_time").toString()));
            ii.setIndexing(toLong(result, "indexing"));
            ii.setPercentIndexed(toLong(result, "percent_indexed"));
            ii.setNumberOfUses(toLong(result, "number_of_uses"));
            ii.setGcStats((Map<String, Object>) result.get("gc_stats"));
            ii.setCursorStats((Map<String, Object>) result.get("cursor_stats"));
            ii.setDialectStats((Map<String, Object>) result.get("dialect_stats"));
            return ii;
        }

        return result;
    }

    private Long toLong(Map<String, Object> result, String prop) {
        if (result.get(prop).toString().contains("nan")) {
            return 0L;
        }
        return Long.valueOf(result.get(prop).toString());
    }
}
