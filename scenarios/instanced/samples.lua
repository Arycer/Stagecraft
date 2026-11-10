local I = require('instanced')
-- Preparation fixture, deliberately retained for subsequent scenarios.
stage.step('preparar samples', function()
stage.host('file.write', {path = "config/instanced/templates/demo/pillar.nbt", base64 = "H4sIAAAAAAAACqXQwWrDMAwG4D+x28bZYS+ww55j553HTrsG11VXUTc2lmCwpx8mtIU0h8AExof/Q0jqAYOnd6/+i4pwGoFncbDCv2RQw+lt6u+wyz6SKvUAbAf74S+ElwuPFIo/6ltOkeVEh+FAlCV6JSyocGKhuKhe7+oWDvvC4TyIei7So/8sKVNRJumwPfrA4zcseVH8v80P1TYO231M4Sx1z52DyUmul2gwVWOwEa2z13o0zQrTrjBmhbEz08wMFmZuZ6ZdMAYOHY3K9UpT/AeiwFUpMQIAAA=="})
stage.host('file.write', {path = "config/instanced/templates/demo/fountain.nbt", base64 = "H4sIAAAAAAAACo3SMU/DMBCG4S8+F5oydIKl/BJmZkbWyI0OyWpiV76TEPx6VFDaqnLCZbGie20/gzcA4eE1aHjnIjEnYCstvMRvJpyGwGpaW9wfw8CqvAHg1/BvYWQ8jTFxX8KHvojmxN2+xP4gmObby/wzKJfz4PkyGLPIV1fd/nh1PIduCEm5JLS42w+5P8jJsmtBxyyT+PwRVqJB+fen3jSGxhkaMjR+oWkM5qm5NjczjTM0ZGiWzM5gdgazM5idwewMZjKYyWAmg5kMZjKYvcHsK+a5Zuk9+4p5rvnvbTQ3d7lK424aQos1J40aWf6u+QEpVDTxpQQAAA=="})
stage.host('file.write', {path = "config/instanced/templates/demo/decor/lantern_post.nbt", base64 = "H4sIAAAAAAAACo3NsQ7CMAwE0GsToCkDYuNbOjMzslamuFLUNqliT3w9CqggwVIv1slP5xow2J9J6cpJfAzAQRys+Acb5CNQALB5O+xmGlmVawBlBXuhiXGafOAuUa/NndLQRhrankPHWMTxK0YKyinAYXsbYzdIrrIOZo6yPPyMwUaUlF/h3xQrTLnCmB9TwKHioF49y5s8AQWVJx4oAQAA"})
stage.host('file.write', {path = "config/instanced/templates/demo/decor/bookshelf_nook.nbt", base64 = "H4sIAAAAAAAACpWPPwvCMBBHf0nrn9RBxMHv4uzs6CrXesXQ2EjvJj+9VKkVLLQGMiTvwbvLgASrAymduBEfa2AtDqn4BydoYX+tw+JOgVU5a7+WSI90Y2xvvuaioVL3eYyVXDmU6OCmh4EL5ab+oF2PLtRU50jVWQLlcJjnIRaVtJnMIblH6YbojkkwEyXl1+vXMRMcO+KYCS0z0DIDzljLTmjZCXvZP/bCl2PhsORavXqWN30C/ylxiRwCAAA="})
stage.host('file.write', {path = "config/instanced/templates/demo/loot/chest_corner.nbt", base64 = "H4sIAAAAAAAACnWPwW7CMBBEx3GAOD3Qf+mNM2fEifvGLMWqsSPvRkh8fQUobaFhpT3szryRpgUs3taktOMiISdgKQ61hAtbXEWgGtdh0VNkVW4B1A3qDZ0Yy1NI7AsddOWPLNqi3Zbcc9HA0mB+IB/SJ2aSBz0CI/b+i3VUCscXXDX0k5CntI/8BC3uX4H5aGBjUNRaBp4OyN2ZOzjMu5j9l9xKOdg+y1j9ZyxmoqR8Ox49ZsJj8D/HPHmqFznmj8fCoeGk4druLn8DfS+V1LIBAAA="})
stage.host('file.write', {path = "config/instanced/pools/demo/decor.json", text = [[{
  "mirror": true,
  "entries": [
    {
      "template": "templates/demo/pillar.nbt",
      "weight": 3
    },
    {
      "template": "templates/demo/fountain.nbt",
      "weight": 1
    },
    {
      "template": "templates/demo/decor/lantern_post.nbt",
      "weight": 2
    },
    {
      "template": null,
      "weight": 1
    }
  ]
}]]})
stage.host('file.write', {path = "config/instanced/pools/demo/loot.json", text = [[{
  "mirror": false,
  "entries": [
    {
      "template": "templates/demo/loot/chest_corner.nbt",
      "weight": 2
    },
    {
      "template": "templates/demo/decor/bookshelf_nook.nbt",
      "weight": 1
    },
    {
      "template": null,
      "weight": 3
    }
  ]
}]]})

end)
