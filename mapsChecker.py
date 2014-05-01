# Quick script to check that none of the broken maps (found using MapChecker.java
# and output to mapsBroken.txt) were used in two different DB's (all map hashes
# from the DB were exported to mapsAll.txt).
# Should also check that these hashes were not used in multiple replays, but
# there is actually a 1-to-1 relation between maps and replays: eg. run:
# use sc_pvp; select count(*) from replay; select count(*) from map;
broken = {}
for line in open("mapsBroken.txt", 'r'):
    line = line.strip()
    if line.startswith('DB'):
        continue
    if line in broken:
       print("dup " + line)
    else:
       broken[line] = 0

for line in open("mapsAll.txt", 'r'):
    line = line.strip()
    if line in broken:
        broken[line] += 1
        
for line in broken:
    if broken[line] != 1:
        print('{} appeared {} times'.format(line, broken[line]))
print('processed {} broken maps, matched with {} used maps'.format(len(broken), sum(broken.values())))