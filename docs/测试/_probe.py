import json, collections, io, sys

path = r"d:/vita/bahealth/registration-assistant/consult-registration-agent/consult-registration-agent-service/src/main/resources/department/catalog-seed.json"
data = json.load(io.open(path, encoding="utf-8"))
print("version", data.get("version"))
print("typos", len(data.get("typos", [])))
print("uncertain", data.get("uncertain"))
print("ambiguous", len(data.get("ambiguous", [])))
print("synonyms", [(s["ruleId"], s["status"]) for s in data.get("synonyms", [])])
total = 0
for dept in data["departments"]:
    specs = dept.get("specialties", [])
    total += len(specs)
    kws = dept.get("keywords", [])
    kinds = collections.Counter(k.get("kind") for k in kws)
    print(f"{dept['code']:>2} {dept['name']:<12} enabled={dept['enabled']} fb={dept['fallback']} gs={dept['genderScope']} child={dept.get('childAgeLt')} specs={len(specs):>2} kw={len(kws)} {dict(kinds)}")
    ov = [k["text"] for k in kws if k.get("kind") == "OVERRIDE"]
    if ov:
        print("      OVERRIDE:", ov)
    if dept.get("routingKeywords"):
        print("      routingKeywords field:", dept["routingKeywords"])
print("DEPTS", len(data["departments"]), "SPECS", total)

kw_total = sum(len(s.get("keywords", [])) for d in data["departments"] for s in d.get("specialties", []))
dis_total = sum(len(s.get("diseases", [])) for d in data["departments"] for s in d.get("specialties", []))
print("spec keyword total", kw_total, "disease total", dis_total)

sg = json.load(io.open(r"d:/vita/bahealth/registration-assistant/consult-registration-agent/consult-registration-agent-service/src/main/resources/safety/urgent-catalog.json", encoding="utf-8"))
print("--- urgent catalog", sg.get("catalogVersion"), "rules", len(sg["rules"]))
for r in sg["rules"]:
    print(r["ruleId"], r["status"], r["disposition"], r.get("negatedPolicy"), r.get("uncertaintyPolicy"), r.get("resolvedPolicy"), len(r["aliases"]), r["aliases"][:6])
