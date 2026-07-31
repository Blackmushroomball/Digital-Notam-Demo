<script setup>
defineProps({
  form: { type: Object, required: true },
  restrictions: { type: Array, required: true },
  addRestriction: { type: Function, required: true },
  removeRestriction: { type: Function, required: true },
  moveRestriction: { type: Function, required: true },
});
</script>

<template>
  <div
    v-if="['AD.LIM', 'RWY.LIM'].includes(form.scenario)"
    class="field-group wide adlim-limits"
  >
    <div class="adlim-title">
      <h3>{{ form.scenario }} 使用限制</h3>
    </div>
    <div class="adlim-common">
      <label
        >限制类型 *<select v-model="form.limitationType">
          <option value="CONDITIONAL">Conditional for</option>
          <option value="RESERV">Closed, except for</option>
          <option value="FORBID">Prohibited for</option>
          <option value="PERMIT">Additionally allowed for</option>
        </select></label
      >
    </div>
    <article
      v-for="(r, index) in restrictions"
      :key="index"
      class="restriction-card"
    >
      <header>
        <b>限制条件 {{ index + 1 }}</b>
        <div>
          <button
            type="button"
            class="link"
            :disabled="index === 0"
            @click="moveRestriction(index, -1)"
          >
            上移</button
          ><button
            type="button"
            class="link"
            :disabled="index === restrictions.length - 1"
            @click="moveRestriction(index, 1)"
          >
            下移</button
          ><button
            type="button"
            class="link delete"
            :disabled="restrictions.length === 1"
            @click="removeRestriction(index)"
          >
            删除
          </button>
        </div>
      </header>
      <div class="adlim-head">
        <label
          >Operation *<select v-model="r.operation">
            <option>ALL</option>
            <option>LANDING</option>
            <option>TAKEOFF</option>
            <option>TOUCHGO</option>
            <option>TRAIN_APPROACH</option>
            <option v-if="form.scenario === 'AD.LIM'">ALTN_LANDING</option>
            <option v-if="form.scenario === 'RWY.LIM'">TAXIING</option>
            <option v-if="form.scenario === 'RWY.LIM'">CROSSING</option>
            <option>AIRSHOW</option>
            <option value="OTHER">OTHER</option>
          </select></label
        ><label v-if="r.operation === 'OTHER'"
          >Operation other *<input v-model="r.operationOther"
        /></label>
      </div>
      <section class="limit-section">
        <label class="limit-toggle"
          ><input v-model="r.includeAircraft" type="checkbox" /><b>Aircraft</b
          ><span>添加航空器限制</span></label
        >
        <div v-if="r.includeAircraft" class="limit-subgrid">
          <label
            >Type<select v-model="r.aircraftType">
              <option value="">不限制</option>
              <option
                v-for="x in [
                  'LANDPLANE',
                  'SEAPLANE',
                  'AMPHIBIAN',
                  'HELICOPTER',
                  'GYROCOPTER',
                  'TILT_WING',
                  'STOL',
                  'GLIDER',
                  'HANGGLIDER',
                  'PARAGLIDER',
                  'ULTRA_LIGHT',
                  'BALLOON',
                  'UAV',
                  'ALL',
                ]"
                :key="x"
              >
                {{ x }}
              </option>
              <option value="OTHER">OTHER</option>
            </select></label
          ><label v-if="r.aircraftType === 'OTHER'"
            >Type other *<input v-model="r.aircraftTypeOther" /></label
          ><label
            >Engine<select v-model="r.aircraftEngine">
              <option value="">不限制</option>
              <option
                v-for="x in ['JET', 'PISTON', 'TURBOPROP', 'ALL']"
                :key="x"
              >
                {{ x }}
              </option>
              <option value="OTHER">OTHER</option>
            </select></label
          ><label v-if="r.aircraftEngine === 'OTHER'"
            >Engine other *<input v-model="r.aircraftEngineOther" /></label
          ><label
            >Wing span<input
              v-model="r.aircraftWingSpan"
              type="number"
              min="0" /></label
          ><label
            >Wing span unit<select v-model="r.aircraftWingSpanUom">
              <option>M</option>
              <option>FT</option>
            </select></label
          ><label
            >Wing span interpretation<select
              v-model="r.aircraftWingSpanInterpretation"
            >
              <option value="">不限制</option>
              <option
                v-for="x in ['ABOVE', 'AT_OR_ABOVE', 'AT_OR_BELOW', 'BELOW']"
                :key="x"
              >
                {{ x }}
              </option>
            </select></label
          ><label
            >Weight<input
              v-model="r.aircraftWeight"
              type="number"
              min="0" /></label
          ><label
            >Weight unit<select v-model="r.aircraftWeightUom">
              <option>KG</option>
              <option>T</option>
              <option>LB</option>
            </select></label
          ><label
            >Weight interpretation<select
              v-model="r.aircraftWeightInterpretation"
            >
              <option value="">不限制</option>
              <option
                v-for="x in ['ABOVE', 'AT_OR_ABOVE', 'AT_OR_BELOW', 'BELOW']"
                :key="x"
              >
                {{ x }}
              </option>
            </select></label
          >
        </div>
      </section>
      <section class="limit-section">
        <label class="limit-toggle"
          ><input v-model="r.includeFlight" type="checkbox" /><b>Flight</b
          ><span>添加飞行限制</span></label
        >
        <div v-if="r.includeFlight" class="limit-subgrid">
          <template
            v-for="field in [
              {
                key: 'flightType',
                label: 'Type',
                values: ['OAT', 'GAT', 'ALL'],
              },
              {
                key: 'flightRule',
                label: 'Rule',
                values: ['IFR', 'VFR', 'ALL'],
              },
              {
                key: 'flightStatus',
                label: 'Status',
                values: [
                  'HEAD',
                  'STATE',
                  'HUM',
                  'HOSP',
                  'SAR',
                  'ALL',
                  'EMERGENCY',
                ],
              },
              {
                key: 'flightMilitary',
                label: 'Military',
                values: ['MIL', 'CIVIL', 'ALL'],
              },
              {
                key: 'flightOrigin',
                label: 'Origin',
                values: ['NTL', 'INTL', 'ALL', 'HOME_BASED'],
              },
              {
                key: 'flightPurpose',
                label: 'Purpose',
                values: [
                  'SCHEDULED',
                  'NON_SCHEDULED',
                  'PRIVATE',
                  'AIR_TRAINING',
                  'AIR_WORK',
                  'ALL',
                  'PARTICIPANT',
                ],
              },
            ]"
            :key="field.key"
            ><label
              >{{ field.label
              }}<select v-model="r[field.key]">
                <option value="">不限制</option>
                <option v-for="x in field.values" :key="x">
                  {{ x }}
                </option>
                <option value="OTHER">OTHER</option>
              </select></label
            ><label v-if="r[field.key] === 'OTHER'"
              >{{ field.label }} other *<input
                v-model="r[`${field.key}Other`]" /></label
          ></template>
        </div>
      </section>
      <section class="limit-section">
        <label class="limit-toggle"
          ><input v-model="r.includePpr" type="checkbox" /><b>PPR</b
          ><span>添加事先许可要求</span></label
        >
        <div v-if="r.includePpr" class="limit-subgrid">
          <label
            >PPR 时间 *<input
              v-model="r.pprValue"
              type="number"
              min="0" /></label
          ><label
            >时间单位 *<select v-model="r.pprUnit">
              <option>HR</option>
              <option>MIN</option>
              <option>SEC</option>
            </select></label
          ><label>PPR details<input v-model="r.pprDetails" /></label>
        </div>
      </section>
    </article>
    <div class="add-restriction">
      <button type="button" class="primary" @click="addRestriction">
        ＋ 添加限制条件
      </button>
    </div>
    <small>XML 与 E 项按卡片顺序生成；Q-code 仅由第一个限制条件确定。</small>
  </div>
</template>
